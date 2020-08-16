package com.xyz.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.xyz.constants.LoanApplicationStatus;
import com.xyz.contracts.LoanApplicationContract;
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
public class LoanApplicationCreationFlow extends FlowLogic<SignedTransaction> {
    private static final Logger LOG = LoggerFactory.getLogger(LoanApplicationCreationFlow.class.getName());

    private String companyName;
    private String businesstype;
    private int loanAmount;
    private LoanApplicationStatus applicationStatus;
    private UniqueIdentifier loanApplicationId;
    private UniqueIdentifier creditCheckApplicationId;

    public LoanApplicationCreationFlow(
            String companyName,
            String businessType,
            int loanAmount, LoanApplicationStatus applicationStatus, UniqueIdentifier loanApplicationId,
            UniqueIdentifier creditCheckApplicationId) {
        this.companyName = companyName;
        this.loanAmount = loanAmount;
        this.businesstype = businessType;
        this.loanApplicationId = loanApplicationId;
        this.creditCheckApplicationId = creditCheckApplicationId;
        this.applicationStatus = applicationStatus;
    }


    private final ProgressTracker progressTracker = tracker();

    private static final ProgressTracker.Step LOAN_REQUESTED = new ProgressTracker.Step("Borrower Requested Loan to Finance Agency");
    private static final ProgressTracker.Step LOAN_VERIFICATION = new ProgressTracker.Step("Loan Application is verified");
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
                LOAN_REQUESTED,
                LOAN_VERIFICATION,
                SIGNING_TRANSACTION,
                COLLECTING_SIGNATURE,
                FINALISING_TRANSACTION
        );
    }


    @Override
    public SignedTransaction call() throws FlowException {
        LOG.info("##### Started Loan request");
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        Party financeParty = getServiceHub().getMyInfo().getLegalIdentities().get(0);
        LoanApplicationState opLoanApplicationState = null;
        StateAndRef<LoanApplicationState> ipLoanApplicationState = null;

        if (applicationStatus == null) {
            opLoanApplicationState = new LoanApplicationState(financeParty, companyName, businesstype, loanAmount, LoanApplicationStatus.APPLIED, new UniqueIdentifier());
        } else {
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

            ipLoanApplicationState = inputStateList.get(0);
//                    new LoanApplicationState(vaultApplicationState.getFinanceAgencyNode(), vaultApplicationState.getCompanyName(), vaultApplicationState.getBusinessType(), vaultApplicationState.getLoanAmount(), vaultApplicationState.getApplicationStatus(), vaultApplicationState.getLoanApplicationId());
            vaultApplicationState.setLoanVerificationId(creditCheckApplicationId);
            vaultApplicationState.setApplicationStatus(LoanApplicationStatus.DECISION_PENDING);
            opLoanApplicationState = vaultApplicationState;
        }
        progressTracker.setCurrentStep(LOAN_REQUESTED);

        Command<LoanApplicationContract.Commands.LoanProcesedForCreditCheck> loanProcessedForCreditCheckCommand = ipLoanApplicationState == null ? null
                : new Command<>(new LoanApplicationContract.Commands.LoanProcesedForCreditCheck(),
                Arrays.asList(opLoanApplicationState.getFinanceAgencyNode().getOwningKey()));

        Command<LoanApplicationContract.Commands.LoanApplied> loanAppliedCommand = loanProcessedForCreditCheckCommand == null ?
                new Command<>(new LoanApplicationContract.Commands.LoanApplied(),
                Arrays.asList(opLoanApplicationState.getFinanceAgencyNode().getOwningKey())) : null;

        final TransactionBuilder txBuilder = loanAppliedCommand != null ? new TransactionBuilder(notary)
                .addOutputState(opLoanApplicationState)
                .addCommand(loanAppliedCommand) : new TransactionBuilder(notary).addInputState(ipLoanApplicationState)
                .addOutputState(opLoanApplicationState)
                .addCommand(loanAppliedCommand);

        txBuilder.verify(getServiceHub());

        progressTracker.setCurrentStep(LOAN_VERIFICATION);

        final SignedTransaction singTransaction = getServiceHub().signInitialTransaction(txBuilder);

        progressTracker.setCurrentStep(SIGNING_TRANSACTION);
        LOG.info("##### Submitting Loan signed request");
        return subFlow(new FinalityFlow(singTransaction));
    }
}


@InitiatedBy(LoanApplicationCreationFlow.class)
class LoanApplicationAcceptor extends FlowLogic<SignedTransaction> {
    private static final Logger LOG = LoggerFactory.getLogger(LoanApplicationAcceptor.class.getName());
    final FlowSession otherPartyFlow;

    public LoanApplicationAcceptor(FlowSession otherPartyFlow) {
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
                    require.using("This must be a transaction between bank and finance Agency (LoanRequestState transaction).", output instanceof LoanApplicationState);
                    return null;
                });
            }
        }
        LOG.info("##### Accepting Loan signed request");
        return subFlow(new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));
    }
}




