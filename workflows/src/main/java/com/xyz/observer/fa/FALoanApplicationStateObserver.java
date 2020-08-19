package com.xyz.observer.fa;

import com.xyz.constants.LoanApplicationStatus;
import com.xyz.flows.fa.BankLoanProcessingInitiationFlow;
import com.xyz.flows.fa.CreditCheckInitiationFlow;
import com.xyz.flows.fa.LoanApplicationCreationFlow;
import com.xyz.states.BankFinanceState;
import com.xyz.states.CreditRatingState;
import com.xyz.states.LoanApplicationState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.DataFeed;
import net.corda.core.node.services.Vault;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.util.concurrent.ExecutionException;

/**
 * This observer observes a event of new Loan Application Creation and initiates a LoanVerfication transaction with the
 * CA. Post which it updates the status of the Loan application to Decision_pending.
 */
public class FALoanApplicationStateObserver {
    private static final Logger logger = LoggerFactory.getLogger(FALoanApplicationStateObserver.class);

    private final CordaRPCOps proxy;
    private final CordaX500Name me;

    public FALoanApplicationStateObserver(CordaRPCOps proxy, CordaX500Name name) {
        this.proxy = proxy;
        this.me = name;
    }

    public void observeLoanApplicationUpdate() {
        try {
            final DataFeed<Vault.Page<LoanApplicationState>, Vault.Update<LoanApplicationState>> loanStateDataFeed = proxy.vaultTrack(LoanApplicationState.class);
            final Observable<Vault.Update<LoanApplicationState>> loanUpdates = loanStateDataFeed.getUpdates();

            loanUpdates.toBlocking().subscribe(update -> update.getProduced().forEach(t -> {

                LoanApplicationState applicationState = t.getState().getData();
                final LoanApplicationStatus applicationStatus = applicationState.getApplicationStatus();
                final UniqueIdentifier applicationId = applicationState.getLoanApplicationId();
                logger.info("LoanApplicationState Update Observed for : " + applicationId.toString() + " and Status : " + applicationStatus.toString());

                if (applicationStatus == LoanApplicationStatus.APPLIED) {
                    new FALoanApplicationStateTrigger(applicationId, applicationStatus, proxy).trigger();
                } else if (applicationStatus == LoanApplicationStatus.CREDIT_SCORE_CHECK_PASS) {
                    new FABankProcessingTrigger(applicationId, proxy).trigger();
                }
            }));

        } catch (Exception e) {
            logger.error("Exception occurred", e.getMessage());
            e.printStackTrace();
        }
    }
}

class FALoanApplicationStateTrigger {
    private static final Logger logger = LoggerFactory.getLogger(FALoanApplicationStateTrigger.class);

    private final UniqueIdentifier loanApplicationId;
    private final CordaRPCOps proxy;
    private final LoanApplicationStatus loanApplicationStatus;

    public FALoanApplicationStateTrigger(UniqueIdentifier loanApplicationId, LoanApplicationStatus applicationStatus, CordaRPCOps proxy) {
        this.loanApplicationId = loanApplicationId;
        this.proxy = proxy;
        this.loanApplicationStatus = applicationStatus;
    }

    public void trigger() {
        try {
            UniqueIdentifier creditCheckApplicationId = null;
            logger.info("Intiating Request to CA for credit check for LoanApplicationID : " + loanApplicationId.toString() + " with status : " + loanApplicationStatus.toString());

            Party creditCheckAgency = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=NewShireCreditRatingAgency,L=New York,C=US"));
            SignedTransaction tx = proxy.startTrackedFlowDynamic(CreditCheckInitiationFlow.class, loanApplicationId, creditCheckAgency).getReturnValue().get();
            creditCheckApplicationId = ((CreditRatingState) tx.getTx().getOutputs().get(0).getData()).getLoanVerificationId();
            logger.info("Credit Check flow initiated with CreditCheck Application Id : " + creditCheckApplicationId.toString() + ". Updating loan application status...");

            SignedTransaction loanUpdateTx = proxy.startTrackedFlowDynamic(LoanApplicationCreationFlow.class, loanApplicationStatus, loanApplicationId, creditCheckApplicationId).getReturnValue().get();
            LoanApplicationState laState = ((LoanApplicationState) loanUpdateTx.getTx().getOutputs().get(0).getData());
            logger.info("Application status for the LoanApplication is updated LoanApplicationID: " + laState.getLoanApplicationId().toString() +
                    " CreditCheckApplicationId : " + laState.getLoanVerificationId().toString() + " Status : " + laState.getApplicationStatus().toString());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error while initiating CreditScoreCheckFlow for loanApplicationId : " + loanApplicationId.getId().toString());
            e.printStackTrace();
        }
    }
}


class FABankProcessingTrigger {
    private static final Logger logger = LoggerFactory.getLogger(FABankProcessingTrigger.class);

    private final UniqueIdentifier loanApplicationID;
    private final CordaRPCOps proxy;

    public FABankProcessingTrigger(UniqueIdentifier loanApplicationId, CordaRPCOps proxy) {
        this.loanApplicationID = loanApplicationId;
        this.proxy = proxy;
    }

    public void trigger() {
        try {
            logger.info("Intiating Bank processing for LoanApplicationID : " + loanApplicationID.toString());

            Party bankNode = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=MTCBank,L=New York,C=US"));
            SignedTransaction bankProcessing = proxy.startTrackedFlowDynamic(BankLoanProcessingInitiationFlow.class, loanApplicationID, bankNode).getReturnValue().get();
            BankFinanceState bankState = ((BankFinanceState) bankProcessing.getTx().getOutputs().get(0).getData());
            logger.info("Loan processing initiated with Bank with Bank Application ID : " + bankState.getBankLoanProcessingId().toString() + " with status : " + bankState.getBankProcessingStatus().toString());

            SignedTransaction loanUpdateTx = proxy.startTrackedFlowDynamic(LoanApplicationCreationFlow.class, bankState.getBankLoanProcessingId(), loanApplicationID, bankState.getBankProcessingStatus()).getReturnValue().get();
            LoanApplicationState laState = ((LoanApplicationState) loanUpdateTx.getTx().getOutputs().get(0).getData());
            logger.info("Application status for the LoanApplication is updated LoanApplicationID: " + laState.getLoanApplicationId().toString() +
                    " CreditCheckApplicationId : " + laState.getLoanVerificationId().toString() + " Status : " + laState.getApplicationStatus().toString());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error while initiating CreditScoreCheckFlow for loanApplicationId : " + loanApplicationID.getId().toString());
            e.printStackTrace();
        }
    }
}