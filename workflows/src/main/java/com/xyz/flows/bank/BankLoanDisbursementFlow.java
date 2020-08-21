package com.xyz.flows.bank;

import co.paralleluniverse.fibers.Suspendable;
import com.xyz.constants.BankProcessingStatus;
import com.xyz.constants.CreditScoreDesc;
import com.xyz.contracts.BankFinanceValidationContract;
import com.xyz.flows.ca.CreditCheckProcessingFlow;
import com.xyz.states.BankFinanceState;
import com.xyz.states.CreditRatingState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static net.corda.core.contracts.ContractsDSL.requireThat;

@InitiatingFlow
@StartableByRPC
public class BankLoanDisbursementFlow extends FlowLogic<SignedTransaction> {
    private static final Logger LOG = LoggerFactory.getLogger(CreditCheckProcessingFlow.class.getName());
    private Party financeAgency;
    private UniqueIdentifier bankLoanProcessingId;
    private Random creditScoreRandom = null;

    public BankLoanDisbursementFlow(
            UniqueIdentifier creditCheckApplicationId, Party financeAgency) {
        this.bankLoanProcessingId = creditCheckApplicationId;
        this.financeAgency = financeAgency;
        this.creditScoreRandom = new Random();
    }

    private final ProgressTracker progressTracker = tracker();

    private static final ProgressTracker.Step BANK_LOAN_REQUESTED = new ProgressTracker.Step("Requesting Credit Score from Credit Rate scoring agency");
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
                BANK_LOAN_REQUESTED,
                CONTRACT_VERIFICATION,
                SIGNING_TRANSACTION,
                COLLECTING_SIGNATURE,
                FINALISING_TRANSACTION
        );
    }


    @Override
    @Suspendable
    public SignedTransaction call() throws FlowException {
        LOG.info("Bank Loan Processing initiated with Verification ID : "+ bankLoanProcessingId.toString());

        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        Party bankNode = getServiceHub().getMyInfo().getLegalIdentities().get(0);

        String companyName = null;
        Long loanAmount = null;
        String businesstype = null;
        CreditScoreDesc creditScoreDesc = null;

        QueryCriteria criteriaApplicationState = new QueryCriteria.LinearStateQueryCriteria(
                null,
                Arrays.asList(bankLoanProcessingId),
                Vault.StateStatus.UNCONSUMED,
                null);

        List<StateAndRef<BankFinanceState>> inputStateList = getServiceHub().getVaultService().queryBy(BankFinanceState.class, criteriaApplicationState).getStates();
        StateAndRef<BankFinanceState> ipBankFinanceState = null;

        if (inputStateList == null || inputStateList.isEmpty()) {
            LOG.error("Application State Cannot be found : " + inputStateList.size() + " " + bankLoanProcessingId.toString());
            throw new IllegalArgumentException("Application State Cannot be found : " + inputStateList.size() + " " + bankLoanProcessingId.toString());
        } else {
            LOG.info("Application State queried from Vault : " + inputStateList.size() + " " + bankLoanProcessingId.toString());
            ipBankFinanceState = inputStateList.get(0);
        }

        companyName = ipBankFinanceState.getState().getData().getCompanyName();
        loanAmount = ipBankFinanceState.getState().getData().getLoanAmount();
        businesstype = ipBankFinanceState.getState().getData().getBusinessType();
        creditScoreDesc = ipBankFinanceState.getState().getData().getCreditScoreDesc();

        progressTracker.setCurrentStep(BANK_LOAN_REQUESTED);

        BankProcessingStatus  processingStatus = creditScoreDesc == CreditScoreDesc.POOR ? BankProcessingStatus.REJECTED : BankProcessingStatus.PROCESSED;

        BankFinanceState financeState =  new BankFinanceState(financeAgency,bankNode,companyName,businesstype,loanAmount,creditScoreDesc,processingStatus,bankLoanProcessingId);

        final Command<BankFinanceValidationContract.Commands.LoanRequestProcessed> bankProcessingCommand = new Command<>(new BankFinanceValidationContract.Commands.LoanRequestProcessed(),
                Arrays.asList(financeState.getBankNode().getOwningKey(), financeState.getFinanceAgencyNode().getOwningKey()));

        final TransactionBuilder txBuilder = new TransactionBuilder(notary).addInputState(ipBankFinanceState)
                .addOutputState(financeState)
                .addCommand(bankProcessingCommand);

        txBuilder.verify(getServiceHub());
        LOG.info("Bank Loan Processing Application Completed : " + bankLoanProcessingId.toString());
        progressTracker.setCurrentStep(CONTRACT_VERIFICATION);

        final SignedTransaction signTx = getServiceHub().signInitialTransaction(txBuilder);

        progressTracker.setCurrentStep(SIGNING_TRANSACTION);

        FlowSession otherPartySession = initiateFlow(financeAgency);
        final SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(signTx, Arrays.asList(otherPartySession), CollectSignaturesFlow.Companion.tracker()));
        progressTracker.setCurrentStep(COLLECTING_SIGNATURE);

        progressTracker.setCurrentStep(FINALISING_TRANSACTION);

        return subFlow(new FinalityFlow(fullySignedTx));
    }
}


@InitiatedBy(BankLoanDisbursementFlow.class)
class BankLoanProcessingAcceptor extends FlowLogic<SignedTransaction> {
    private static final Logger LOG = LoggerFactory.getLogger(BankLoanProcessingAcceptor.class.getName());
    final FlowSession otherPartyFlow;

    public BankLoanProcessingAcceptor(FlowSession otherPartyFlow) {
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
                    require.using("This must be a transaction between bank and finance Agency (BankFinanceState transaction).", output instanceof BankFinanceState);
                    return null;
                });
            }
        }
        LOG.info("##### Accepting Credit state signed request");
        return subFlow(new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));
    }
}




