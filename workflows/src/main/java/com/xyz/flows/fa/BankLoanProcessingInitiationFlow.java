package com.xyz.flows.fa;

import co.paralleluniverse.fibers.Suspendable;
import com.xyz.constants.BankProcessingStatus;
import com.xyz.constants.CreditScoreDesc;
import com.xyz.contracts.BankFinanceValidationContract;
import com.xyz.states.BankFinanceState;
import com.xyz.states.CreditRatingState;
import com.xyz.states.LoanApplicationState;
import com.xyz.states.schema.LoaningProcessSchemas;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.Builder;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.QueryCriteriaUtils;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireThat;

@InitiatingFlow
@StartableByRPC
public class BankLoanProcessingInitiationFlow extends FlowLogic<SignedTransaction> {
    private static final Logger LOG = LoggerFactory.getLogger(BankLoanProcessingInitiationFlow.class.getName());
    private Party bankNode;
    private UniqueIdentifier loanApplicationId;

    public BankLoanProcessingInitiationFlow(
            UniqueIdentifier loanApplicationId, Party bankNode) {
        this.loanApplicationId = loanApplicationId;
        this.bankNode = bankNode;
    }

    private final ProgressTracker progressTracker = tracker();

    private static final ProgressTracker.Step BANK_PROCESSING_INITIATED = new ProgressTracker.Step("Requesting Credit Score from Credit Rate scoring agency");
    private static final ProgressTracker.Step CONTRACT_VERIFICATION = new ProgressTracker.Step("Verified the contract between FA and XYZ Node");
    private static final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing the transaction");
    private static final ProgressTracker.Step COLLECTING_SIGNATURE = new ProgressTracker.Step("Collection Signature from Finance Agency and Bank");
    private static final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Recording transaction") {
        @Override
        public ProgressTracker childProgressTracker() {
            return FinalityFlow.tracker();
        }
    };

    private static ProgressTracker tracker() {
        return new ProgressTracker(
                BANK_PROCESSING_INITIATED,
                CONTRACT_VERIFICATION,
                SIGNING_TRANSACTION,
                COLLECTING_SIGNATURE,
                FINALISING_TRANSACTION
        );
    }

    @Override
    @Suspendable
    public SignedTransaction call() throws FlowException {
        LOG.info("##### Started Request for CreditCheck flow");
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        Party financeNode = getServiceHub().getMyInfo().getLegalIdentities().get(0);

        String companyName = null;
        Long loanAmount = null;
        String businesstype = null;
        CreditScoreDesc creditScoreDesc = null;
        UniqueIdentifier bankProcessingId = new UniqueIdentifier();

        QueryCriteria criteriaApplicationState = new QueryCriteria.LinearStateQueryCriteria(
                null,
                Arrays.asList(loanApplicationId),
                Vault.StateStatus.UNCONSUMED,
                null);

        List<StateAndRef<LoanApplicationState>> inputStateList = getServiceHub().getVaultService().queryBy(LoanApplicationState.class, criteriaApplicationState).getStates();
        StateAndRef<LoanApplicationState> ipLoanApplicationState = null;

        if (inputStateList == null || inputStateList.isEmpty()) {
            LOG.error("Application State Cannot be found : " + inputStateList.size() + " " + loanApplicationId.toString());
            throw new IllegalArgumentException("Application State Cannot be found : " + inputStateList.size() + " " + loanApplicationId.toString());
        } else {
            LOG.info("Application State queried from Vault : " + inputStateList.size() + " " + loanApplicationId.toString());
            ipLoanApplicationState = inputStateList.get(0);
        }

        final LoanApplicationState laState = ipLoanApplicationState.getState().getData();
        QueryCriteria creditVerficationIdCustomQuery = null;
        try {
            creditVerficationIdCustomQuery = new QueryCriteria.VaultCustomQueryCriteria(
                    Builder.equal(QueryCriteriaUtils.getField("loanVerificationId",
                            LoaningProcessSchemas.PersistentCreditRatingSchema.class), laState.getLoanVerificationId().getId()));
        } catch (Exception e) {
            e.printStackTrace();
            throw new FlowException("Field name loanVerificationId does not exist in PersistentCreditRatingSchema class , " + e.getMessage());
        }
        List<StateAndRef<CreditRatingState>> vaultCreditRatingStates = getServiceHub().getVaultService().
                queryBy(CreditRatingState.class, creditVerficationIdCustomQuery).getStates();

        CreditRatingState ratingState = vaultCreditRatingStates.get(0).getState().getData();

        companyName = ipLoanApplicationState.getState().getData().getCompanyName();
        loanAmount = ipLoanApplicationState.getState().getData().getLoanAmount();
        businesstype = ipLoanApplicationState.getState().getData().getBusinessType();
        creditScoreDesc = ratingState.getCreditScoreDesc();

        progressTracker.setCurrentStep(BANK_PROCESSING_INITIATED);

        BankFinanceState bankFinanceState = new BankFinanceState(financeNode, bankNode, companyName, businesstype, loanAmount,
                creditScoreDesc, BankProcessingStatus.IN_PROCESSING, bankProcessingId);

        final Command<BankFinanceValidationContract.Commands.BankProcessingInitiated> bankProcessingCommand =
                new Command<BankFinanceValidationContract.Commands.BankProcessingInitiated>(new BankFinanceValidationContract.Commands.BankProcessingInitiated(),
                        Arrays.asList(bankFinanceState.getFinanceAgencyNode().getOwningKey(), bankFinanceState.getBankNode().getOwningKey()));

        final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                .addOutputState(bankFinanceState)
                .addCommand(bankProcessingCommand);

        txBuilder.verify(getServiceHub());
        LOG.info("Bank processing initiated : " + bankProcessingId.toString());
        progressTracker.setCurrentStep(CONTRACT_VERIFICATION);

        final SignedTransaction signTx = getServiceHub().signInitialTransaction(txBuilder);

        progressTracker.setCurrentStep(SIGNING_TRANSACTION);

        FlowSession otherPartySession = initiateFlow(bankNode);
        SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(signTx, Arrays.asList(otherPartySession), CollectSignaturesFlow.Companion.tracker()));
        progressTracker.setCurrentStep(COLLECTING_SIGNATURE);

        progressTracker.setCurrentStep(FINALISING_TRANSACTION);

        return subFlow(new FinalityFlow(fullySignedTx));
    }
}

@InitiatedBy(BankLoanProcessingInitiationFlow.class)
class BankProcessingInitiationAcceptor extends FlowLogic<SignedTransaction> {
    private static final Logger LOG = LoggerFactory.getLogger(BankProcessingInitiationAcceptor.class.getName());
    final FlowSession otherPartyFlow;

    public BankProcessingInitiationAcceptor(FlowSession otherPartyFlow) {
        this.otherPartyFlow = otherPartyFlow;
    }

    @Override
    @Suspendable
    public SignedTransaction call() throws FlowException {
        class SignTxFlow extends SignTransactionFlow {
            public SignTxFlow(FlowSession otherSideSession, ProgressTracker progressTracker) {
                super(otherSideSession, progressTracker);
            }

            @Override
            protected void checkTransaction(SignedTransaction stx) throws FlowException {
                requireThat(require -> {
                    ContractState output = stx.getTx().getOutputs().get(0).getData();
                    require.using("This must be a transaction between finance agency and bank.", output instanceof BankFinanceState);
                    return null;
                });
            }
        }
        LOG.info("##### Accepting Bank Processing State signed request");
        return subFlow(new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));
    }
}




