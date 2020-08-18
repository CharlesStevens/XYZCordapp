package com.xyz.flows.fa;

import co.paralleluniverse.fibers.Suspendable;
import com.xyz.constants.BankProcessingStatus;
import com.xyz.constants.CreditScoreDesc;
import com.xyz.constants.LoanApplicationStatus;
import com.xyz.contracts.BankFinanceValidationContract;
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
    private UniqueIdentifier bankProcessingId;
    private BankProcessingStatus bankProcessingStatus;

    private CreditScoreDesc scoreDesc;
    private Double creditScore;

    public LoanApplicationCreationFlow(
            String companyName,
            String businessType,
            int loanAmount) {
        this.companyName = companyName;
        this.loanAmount = loanAmount;
        this.businesstype = businessType;

    }

    public LoanApplicationCreationFlow(LoanApplicationStatus applicationStatus, UniqueIdentifier loanApplicationId,
                                       UniqueIdentifier creditCheckApplicationId) {
        this.loanApplicationId = loanApplicationId;
        this.creditCheckApplicationId = creditCheckApplicationId;
        this.applicationStatus = applicationStatus;
    }

    public LoanApplicationCreationFlow(UniqueIdentifier creditCheckApplicationId, CreditScoreDesc scoreDesc, Double creditScore) {
        this.creditCheckApplicationId = creditCheckApplicationId;
        this.scoreDesc = scoreDesc;
        this.creditScore = creditScore;
    }

    public LoanApplicationCreationFlow(UniqueIdentifier bankProcessingId, UniqueIdentifier loanApplicationId, BankProcessingStatus bankProcessingStatus) {
        this.bankProcessingId = bankProcessingId;
        this.loanApplicationId = loanApplicationId;
        this.bankProcessingStatus = bankProcessingStatus;
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
        LOG.info("Started Loan request creating/update");
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        Party financeParty = getServiceHub().getMyInfo().getLegalIdentities().get(0);
        LoanApplicationState opLoanApplicationState = null;
        StateAndRef<LoanApplicationState> ipLoanApplicationState = null;
        TransactionBuilder txBuilder = null;

        if (applicationStatus == null && scoreDesc == null) {
            opLoanApplicationState = new LoanApplicationState(financeParty, companyName, businesstype, loanAmount, LoanApplicationStatus.APPLIED, new UniqueIdentifier(), null, null);
            Command<LoanApplicationContract.Commands.LoanApplied> loanAppliedCommand =
                    new Command<>(new LoanApplicationContract.Commands.LoanApplied(),
                            Arrays.asList(opLoanApplicationState.getFinanceAgencyNode().getOwningKey()));

            txBuilder = new TransactionBuilder(notary)
                    .addOutputState(opLoanApplicationState)
                    .addCommand(loanAppliedCommand);

        } else if (this.scoreDesc == null) {
            QueryCriteria criteriaApplicationState = new QueryCriteria.LinearStateQueryCriteria(
                    null,
                    Arrays.asList(loanApplicationId),
                    Vault.StateStatus.UNCONSUMED,
                    null);

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
            vaultApplicationState.setLoanVerificationId(creditCheckApplicationId);
            vaultApplicationState.setApplicationStatus(LoanApplicationStatus.FORWARDED_TO_CREDIT_CHECK_AGENCY);
            opLoanApplicationState = vaultApplicationState;

            Command<LoanApplicationContract.Commands.LoanApplied> loanAppliedCommand =
                    new Command<>(new LoanApplicationContract.Commands.LoanApplied(),
                            Arrays.asList(opLoanApplicationState.getFinanceAgencyNode().getOwningKey()));

            txBuilder = new TransactionBuilder(notary).addInputState(ipLoanApplicationState)
                    .addOutputState(opLoanApplicationState)
                    .addCommand(loanAppliedCommand);
        } else if (creditCheckApplicationId != null && scoreDesc != null) {
            List<StateAndRef<LoanApplicationState>> inputStateList = getServiceHub().getVaultService().queryBy(LoanApplicationState.class).getStates();
            StateAndRef<LoanApplicationState> inputState = inputStateList.stream().filter(t -> t.getState().getData().getLoanVerificationId().toString().equals(creditCheckApplicationId.toString())).findAny().get();
            LoanApplicationState vaultApplicationState = inputState.getState().getData();

            ipLoanApplicationState = inputState;
            if (scoreDesc.equals(CreditScoreDesc.GOOD) || scoreDesc.equals(CreditScoreDesc.FAIR))
                vaultApplicationState.setApplicationStatus(LoanApplicationStatus.CREDIT_SCORE_CHECK_PASS);
            else vaultApplicationState.setApplicationStatus(LoanApplicationStatus.CREDIT_SCORE_CHECK_FAILED);
            opLoanApplicationState = vaultApplicationState;

            Command<LoanApplicationContract.Commands.LoanProcesedFromCreditCheck> loanProcessedForCreditCheckCommand =
                    new Command<>(new LoanApplicationContract.Commands.LoanProcesedFromCreditCheck(),
                            Arrays.asList(opLoanApplicationState.getFinanceAgencyNode().getOwningKey()));

            txBuilder = new TransactionBuilder(notary).addInputState(ipLoanApplicationState)
                    .addOutputState(opLoanApplicationState)
                    .addCommand(loanProcessedForCreditCheckCommand);

        } else if (bankProcessingId != null && bankProcessingStatus != null) {
            List<StateAndRef<LoanApplicationState>> inputStateList = getServiceHub().getVaultService().queryBy(LoanApplicationState.class).getStates();
            StateAndRef<LoanApplicationState> inputState = bankProcessingStatus == BankProcessingStatus.IN_PROCESSING ? inputStateList.stream().filter(t -> t.getState().getData().getLoanApplicationId().toString().equals(loanApplicationId.toString())).findAny().get()
                    : inputStateList.stream().filter(t -> t.getState().getData().getBankProcessingId().toString().equals(bankProcessingId.toString())).findAny().get();
            LoanApplicationState vaultApplicationState = inputState.getState().getData();

            ipLoanApplicationState = inputState;

            if (bankProcessingStatus.equals(BankProcessingStatus.IN_PROCESSING)) {
                vaultApplicationState.setBankProcessingId(bankProcessingId);
                vaultApplicationState.setApplicationStatus(LoanApplicationStatus.FORWARDED_TO_BANK);
            } else if (bankProcessingStatus.equals(BankProcessingStatus.PROCESSED)) {
                vaultApplicationState.setApplicationStatus(LoanApplicationStatus.LOAN_DISBURSED);
            } else {
                vaultApplicationState.setApplicationStatus(LoanApplicationStatus.REJECTED_FROM_BANK);
            }

            opLoanApplicationState = vaultApplicationState;

            Command<BankFinanceValidationContract.Commands.LoanRequestProcessed> loanProcessedFromBankCommand =
                    new Command<>(new BankFinanceValidationContract.Commands.LoanRequestProcessed(),
                            Arrays.asList(opLoanApplicationState.getFinanceAgencyNode().getOwningKey()));

            txBuilder = new TransactionBuilder(notary).addInputState(ipLoanApplicationState)
                    .addOutputState(opLoanApplicationState)
                    .addCommand(loanProcessedFromBankCommand);
        }
        progressTracker.setCurrentStep(LOAN_REQUESTED);

        txBuilder.verify(getServiceHub());

        progressTracker.setCurrentStep(LOAN_VERIFICATION);

        final SignedTransaction singTransaction = getServiceHub().signInitialTransaction(txBuilder);

        progressTracker.setCurrentStep(SIGNING_TRANSACTION);
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




