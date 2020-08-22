package com.xyz.flows.fa;

import co.paralleluniverse.fibers.Suspendable;
import com.xyz.constants.BankProcessingStatus;
import com.xyz.constants.CreditScoreDesc;
import com.xyz.constants.LoanApplicationStatus;
import com.xyz.contracts.LoanApplicationContract;
import com.xyz.states.LoanApplicationState;
import com.xyz.states.schema.LoaningProcessSchemas;
import net.corda.core.contracts.*;
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
public class LoanApplicationCreationFlow extends FlowLogic<SignedTransaction> {
    private static final Logger LOG = LoggerFactory.getLogger(LoanApplicationCreationFlow.class.getName());

    private String companyName;
    private String businesstype;
    private Long loanAmount;
    private LoanApplicationStatus applicationStatus;
    private UniqueIdentifier loanApplicationId;
    private UniqueIdentifier creditCheckApplicationId;
    private UniqueIdentifier bankProcessingId;
    private BankProcessingStatus bankProcessingStatus;

    private CreditScoreDesc scoreDesc;

    public LoanApplicationCreationFlow(String companyName, String businessType, Long loanAmount) {
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

    public LoanApplicationCreationFlow(UniqueIdentifier creditCheckApplicationId, CreditScoreDesc scoreDesc) {
        this.creditCheckApplicationId = creditCheckApplicationId;
        this.scoreDesc = scoreDesc;
    }

    public LoanApplicationCreationFlow(UniqueIdentifier bankProcessingId, UniqueIdentifier loanApplicationId,
                                       BankProcessingStatus bankProcessingStatus) {
        this.bankProcessingId = bankProcessingId;
        this.loanApplicationId = loanApplicationId;
        this.bankProcessingStatus = bankProcessingStatus;
    }

    public LoanApplicationCreationFlow(UniqueIdentifier bankProcessingId, BankProcessingStatus bankProcessingStatus) {
        this.bankProcessingId = bankProcessingId;
        this.bankProcessingStatus = bankProcessingStatus;
    }

    private final ProgressTracker progressTracker = tracker();

    private static final ProgressTracker.Step LOAN_REQUESTED = new ProgressTracker.Step(
            "Borrower Requested Loan to Finance Agency");
    private static final ProgressTracker.Step LOAN_VERIFICATION = new ProgressTracker.Step(
            "Loan Application is verified");
    private static final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing the transaction");
    private static final ProgressTracker.Step COLLECTING_SIGNATURE = new ProgressTracker.Step(
            "Collection Signature from Finance Agency and Bank");

    private static final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step(
            "Recording transaction") {
        @Override
        public ProgressTracker childProgressTracker() {
            return FinalityFlow.tracker();
        }
    };

    private static ProgressTracker tracker() {
        return new ProgressTracker(LOAN_REQUESTED, LOAN_VERIFICATION, SIGNING_TRANSACTION, COLLECTING_SIGNATURE,
                FINALISING_TRANSACTION);
    }

    @Override
    @Suspendable
    public SignedTransaction call() throws FlowException {
        LOG.info("Started Loan request creating/update");
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        Party financeParty = getServiceHub().getMyInfo().getLegalIdentities().get(0);
        LoanApplicationState opLoanApplicationState = null;
        StateAndRef<LoanApplicationState> ipLoanApplicationState = null;
        TransactionBuilder txBuilder = null;

        if (applicationStatus == null && scoreDesc == null && bankProcessingStatus == null) {
            opLoanApplicationState = new LoanApplicationState(financeParty, companyName, businesstype, loanAmount,
                    LoanApplicationStatus.APPLIED, new UniqueIdentifier(), null, null);
            Command<LoanApplicationContract.Commands.LoanApplied> loanAppliedCommand = new Command<>(
                    new LoanApplicationContract.Commands.LoanApplied(),
                    Arrays.asList(opLoanApplicationState.getFinanceAgencyNode().getOwningKey()));

            txBuilder = new TransactionBuilder(notary).addOutputState(opLoanApplicationState)
                    .addCommand(loanAppliedCommand);

        } else if (this.scoreDesc == null && bankProcessingStatus == null) {
            QueryCriteria criteriaApplicationState = new QueryCriteria.LinearStateQueryCriteria(null,
                    Arrays.asList(loanApplicationId), Vault.StateStatus.UNCONSUMED, null);

            List<StateAndRef<LoanApplicationState>> inputStateList = getServiceHub().getVaultService()
                    .queryBy(LoanApplicationState.class, criteriaApplicationState).getStates();
            LoanApplicationState vaultApplicationState = null;

            if (inputStateList == null || inputStateList.isEmpty()) {
                LOG.error("Application State Cannot be found : " + inputStateList.size() + " "
                        + loanApplicationId.toString());
                throw new IllegalArgumentException("Application State Cannot be found : " + inputStateList.size() + " "
                        + loanApplicationId.toString());
            } else {
                LOG.info("Application State queried from Vault : " + inputStateList.size() + " "
                        + loanApplicationId.toString());
                vaultApplicationState = inputStateList.get(0).getState().getData();
            }

            ipLoanApplicationState = inputStateList.get(0);
            vaultApplicationState.setLoanVerificationId(creditCheckApplicationId);
            vaultApplicationState.setApplicationStatus(LoanApplicationStatus.FORWARDED_TO_CREDIT_CHECK_AGENCY);
            opLoanApplicationState = vaultApplicationState;
            Command<LoanApplicationContract.Commands.LoanForwardedToCreditCheck> loanAppliedCommand = new Command<>(
                    new LoanApplicationContract.Commands.LoanForwardedToCreditCheck(),
                    Arrays.asList(opLoanApplicationState.getFinanceAgencyNode().getOwningKey()));

            txBuilder = new TransactionBuilder(notary).addInputState(ipLoanApplicationState)
                    .addOutputState(opLoanApplicationState).addCommand(loanAppliedCommand);
        } else if (creditCheckApplicationId != null && scoreDesc != null && bankProcessingStatus == null) {
            QueryCriteria creditVerficationIdCustomQuery = null;
            try {
                creditVerficationIdCustomQuery = new QueryCriteria.VaultCustomQueryCriteria(
                        Builder.equal(QueryCriteriaUtils.getField("loanVerificationId",
                                LoaningProcessSchemas.PersistentLoanApplicationState.class), creditCheckApplicationId.getId()));
            } catch (Exception e) {
                e.printStackTrace();
                throw new FlowException("Field name loanVerificationId does not exist in PersistentLoanApplicationState class , " + e.getMessage());
            }
            List<StateAndRef<LoanApplicationState>> laonApplicationStates = getServiceHub().getVaultService().
                    queryBy(LoanApplicationState.class, creditVerficationIdCustomQuery).getStates();

            StateAndRef<LoanApplicationState> inputState = laonApplicationStates.stream().filter(t -> t.getState().getData()
                    .getLoanVerificationId().toString().equals(creditCheckApplicationId.toString())).findAny().get();
            LoanApplicationState vaultApplicationState = inputState.getState().getData();

            ipLoanApplicationState = inputState;
            if (scoreDesc.equals(CreditScoreDesc.GOOD) || scoreDesc.equals(CreditScoreDesc.FAIR))
                vaultApplicationState.setApplicationStatus(LoanApplicationStatus.CREDIT_SCORE_CHECK_PASS);
            else
                vaultApplicationState.setApplicationStatus(LoanApplicationStatus.CREDIT_SCORE_CHECK_FAILED);
            opLoanApplicationState = vaultApplicationState;

            Command<LoanApplicationContract.Commands.LoanProcesedFromCreditCheck> loanProcessedForCreditCheckCommand = new Command<>(
                    new LoanApplicationContract.Commands.LoanProcesedFromCreditCheck(),
                    Arrays.asList(opLoanApplicationState.getFinanceAgencyNode().getOwningKey()));

            txBuilder = new TransactionBuilder(notary).addInputState(ipLoanApplicationState)
                    .addOutputState(opLoanApplicationState).addCommand(loanProcessedForCreditCheckCommand);

        } else if (bankProcessingId != null && bankProcessingStatus != null) {
            StateAndRef<LoanApplicationState> inputState = null;
            LOG.info("##### Bank processing Sttus : " + bankProcessingStatus.toString() + " loanApplicationID : " +
                    (loanApplicationId == null ? "" : loanApplicationId.getId().toString()) + " bankApplicationId : " +
                    (bankProcessingId == null ? "" : bankProcessingId.getId().toString()));
            if (bankProcessingStatus == BankProcessingStatus.IN_PROCESSING) {
                QueryCriteria loanStateCustomQuery = null;
                try {
                    loanStateCustomQuery = new QueryCriteria.VaultCustomQueryCriteria(
                            Builder.equal(QueryCriteriaUtils.getField("loanApplicationId",
                                    LoaningProcessSchemas.PersistentLoanApplicationState.class), loanApplicationId.getId()));
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new FlowException("Field name loanApplicationId does not exist in PersistentLoanApplicationState class , " + e.getMessage());
                }
                inputState = getServiceHub().getVaultService().
                        queryBy(LoanApplicationState.class, loanStateCustomQuery).getStates().get(0);
            } else {
                QueryCriteria creditVerficationIdCustomQuery = null;
                try {
                    creditVerficationIdCustomQuery = new QueryCriteria.VaultCustomQueryCriteria(
                            Builder.equal(QueryCriteriaUtils.getField("bankProcessingId",
                                    LoaningProcessSchemas.PersistentLoanApplicationState.class), bankProcessingId.getId()));
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new FlowException("Field name bankProcessingId does not exist in PersistentLoanApplicationState class , " + e.getMessage());
                }
                inputState = getServiceHub().getVaultService().
                        queryBy(LoanApplicationState.class, creditVerficationIdCustomQuery).getStates().get(0);
            }

            LoanApplicationState vaultApplicationState = inputState.getState().getData();
            ipLoanApplicationState = inputState;
            Command<CommandData> commandData = null;

            if (bankProcessingStatus.equals(BankProcessingStatus.IN_PROCESSING)) {
                vaultApplicationState.setBankProcessingId(bankProcessingId);
                vaultApplicationState.setApplicationStatus(LoanApplicationStatus.FORWARDED_TO_BANK);
                opLoanApplicationState = vaultApplicationState;

                commandData = new Command<>(new LoanApplicationContract.Commands.LoanApplicationForwaredToBank(),
                        Arrays.asList(opLoanApplicationState.getFinanceAgencyNode().getOwningKey()));
            } else {
                if (bankProcessingStatus.equals(BankProcessingStatus.PROCESSED)) {
                    vaultApplicationState.setApplicationStatus(LoanApplicationStatus.LOAN_DISBURSED);
                } else {
                    vaultApplicationState.setApplicationStatus(LoanApplicationStatus.REJECTED_FROM_BANK);
                }
                opLoanApplicationState = vaultApplicationState;
                commandData = new Command<>(new LoanApplicationContract.Commands.LoanProcessedFromBank(),
                        Arrays.asList(opLoanApplicationState.getFinanceAgencyNode().getOwningKey()));
            }

            txBuilder = new TransactionBuilder(notary).addInputState(ipLoanApplicationState)
                    .addOutputState(opLoanApplicationState).addCommand(commandData);
        }
        progressTracker.setCurrentStep(LOAN_REQUESTED);

        txBuilder.verify(getServiceHub());

        progressTracker.setCurrentStep(LOAN_VERIFICATION);

        SignedTransaction singTransaction = getServiceHub().signInitialTransaction(txBuilder);

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
                    require.using(
                            "This must be a transaction between bank and finance Agency (LoanRequestState transaction).",
                            output instanceof LoanApplicationState);
                    return null;
                });
            }
        }
        LOG.info("##### Accepting Loan signed request");
        return subFlow(new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));
    }
}
