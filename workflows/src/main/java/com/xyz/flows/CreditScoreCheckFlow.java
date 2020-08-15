package com.xyz.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.xyz.constants.CreditScoreDesc;
import com.xyz.contracts.CreditRatingCheckContract;
import com.xyz.states.CreditRatingState;
import com.xyz.states.LoanApplicationState;
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

import static net.corda.core.contracts.ContractsDSL.requireThat;

@InitiatingFlow
@StartableByRPC
public class CreditScoreCheckFlow extends FlowLogic<SignedTransaction> {
    private static final Logger LOG = LoggerFactory.getLogger(CreditScoreCheckFlow.class.getName());
    private Party creditScoreCheckAgency;
    private UniqueIdentifier loanApplicationId;

    public CreditScoreCheckFlow(
            UniqueIdentifier loanApplicationId, Party creditAgency) {
        this.loanApplicationId = loanApplicationId;
        this.creditScoreCheckAgency = creditAgency;
    }

    private final ProgressTracker progressTracker = tracker();

    private static final ProgressTracker.Step CREDIT_SCORE_REQUESTED = new ProgressTracker.Step("Requesting Credit Score from Credit Rate scoring agency");
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
                CREDIT_SCORE_REQUESTED,
                CONTRACT_VERIFICATION,
                SIGNING_TRANSACTION,
                COLLECTING_SIGNATURE,
                FINALISING_TRANSACTION
        );
    }


    @Override
    @Suspendable
    public SignedTransaction call() throws FlowException {
        LOG.info("##### Started Processing Credit Check flow");

        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        Party spirseNode = getServiceHub().getMyInfo().getLegalIdentities().get(0);

        String companyName = null;
        Integer loanAmount = null;
        String businesstype = null;

        UniqueIdentifier loanVerificationId = null;

        QueryCriteria criteriaApplicationState = new QueryCriteria.LinearStateQueryCriteria(
                null,
                Arrays.asList(loanApplicationId),
                Vault.StateStatus.UNCONSUMED,
                null);

        StateAndRef<LoanApplicationState> inputState = null;
        List<StateAndRef<LoanApplicationState>> inputStateList = getServiceHub().getVaultService().queryBy(LoanApplicationState.class, criteriaApplicationState).getStates();
        LoanApplicationState vaultApplicationState = null;

        if (inputStateList == null || inputStateList.isEmpty()) {
            LOG.error("Application State Cannot be found : " + inputStateList.size() + " " + loanApplicationId.toString());
            throw new IllegalArgumentException("Application State Cannot be found : " + inputStateList.size() + " " + loanApplicationId.toString());
        } else {
            LOG.info("Application State queried from Vault : " + inputStateList.size() + " " + loanApplicationId.toString());
            vaultApplicationState = inputStateList.get(0).getState().getData();
        }

        companyName = vaultApplicationState.getCompanyName();
        loanAmount = vaultApplicationState.getLoanAmount();
        loanVerificationId = new UniqueIdentifier();

        progressTracker.setCurrentStep(CREDIT_SCORE_REQUESTED);

        CreditRatingState creditState = new CreditRatingState(spirseNode, creditScoreCheckAgency, companyName, businesstype, loanAmount, 0.0, CreditScoreDesc.UNSPECIFIED, loanVerificationId);

        final Command<CreditRatingCheckContract.Commands.CreditCheckRequest> creditScoreCheckRequestCommand = new Command<CreditRatingCheckContract.Commands.CreditCheckRequest>(new CreditRatingCheckContract.Commands.CreditCheckRequest(),
                Arrays.asList(creditState.getLoaningAgency().getOwningKey(), creditState.getCreditAgencyNode().getOwningKey()));

        final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                .addOutputState(creditState)
                .addCommand(creditScoreCheckRequestCommand);

        txBuilder.verify(getServiceHub());
        LOG.info("Credit Score request check initiated with Verification ID : " + loanVerificationId.toString());
        progressTracker.setCurrentStep(CONTRACT_VERIFICATION);

        final SignedTransaction signTx = getServiceHub().signInitialTransaction(txBuilder);

        progressTracker.setCurrentStep(SIGNING_TRANSACTION);

        FlowSession otherPartySession = initiateFlow(creditScoreCheckAgency);
        final SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(signTx, Arrays.asList(otherPartySession), CollectSignaturesFlow.Companion.tracker()));
        progressTracker.setCurrentStep(COLLECTING_SIGNATURE);

        progressTracker.setCurrentStep(FINALISING_TRANSACTION);

        return subFlow(new FinalityFlow(fullySignedTx));
    }
}


@InitiatedBy(CreditScoreCheckFlow.class)
class CreditScoreAcceptor extends FlowLogic<SignedTransaction> {
    private static final Logger LOG = LoggerFactory.getLogger(LoanApplicationAcceptor.class.getName());
    final FlowSession otherPartyFlow;

    public CreditScoreAcceptor(FlowSession otherPartyFlow) {
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
                    require.using("This must be a transaction between bank and finance Agency (LoanRequestState transaction).", output instanceof CreditRatingState);
                    return null;
                });
            }
        }
        LOG.info("##### Accepting Credit state signed request");
        return subFlow(new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));
    }
}




