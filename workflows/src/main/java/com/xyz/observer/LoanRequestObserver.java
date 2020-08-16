package com.xyz.observer;

import com.xyz.constants.CreditScoreDesc;
import com.xyz.constants.LoanApplicationStatus;
import com.xyz.flows.CreditCheckInitiationFlow;
import com.xyz.flows.LoanApplicationCreationFlow;
import com.xyz.states.CreditRatingState;
import com.xyz.states.LoanApplicationState;
import net.corda.core.contracts.StateAndRef;
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

public class LoanRequestObserver {
    private static final Logger logger = LoggerFactory.getLogger(LoanRequestObserver.class);

    private final CordaRPCOps proxy;
    private final CordaX500Name me;

    public LoanRequestObserver(CordaRPCOps proxy, CordaX500Name name) {
        this.proxy = proxy;
        this.me = name;
    }

    private static void logState(StateAndRef<LoanApplicationState> state) {
        logger.info("{}", state.getState().getData());
    }

    public void observeLoanApplicationUpdate() {
        try {
            final DataFeed<Vault.Page<LoanApplicationState>, Vault.Update<LoanApplicationState>> loanStateDataFeed = proxy.vaultTrack(LoanApplicationState.class);
            final Observable<Vault.Update<LoanApplicationState>> loanUpdates = loanStateDataFeed.getUpdates();

            final DataFeed<Vault.Page<CreditRatingState>, Vault.Update<CreditRatingState>> creditStateDataFeed = proxy.vaultTrack(CreditRatingState.class);
            final Observable<Vault.Update<CreditRatingState>> creditUpdates = creditStateDataFeed.getUpdates();

            loanUpdates.toBlocking().subscribe(update -> update.getProduced().forEach(t -> {

                LoanApplicationState applicationState = t.getState().getData();
                logger.info("Update in XYZ Node for Loan Application Id : " + applicationState.getLoanApplicationId() + " Detected.");
                final LoanApplicationStatus applicationStatus = applicationState.getApplicationStatus();
                final UniqueIdentifier applicationId = applicationState.getLoanApplicationId();

                logger.info("loan Application status : " + applicationStatus);

                if (applicationStatus == LoanApplicationStatus.APPLIED) {
                    logger.info("Initiating Credit Check flow from observer for Loan Application ID  : " + applicationId);
                    Thread newThreadCreditCheckFlow = new Thread(new InitiateCreditCheckFlow(applicationId, applicationStatus, proxy));
                    newThreadCreditCheckFlow.start();
                }
            }));

            creditUpdates.toBlocking().subscribe(update -> update.getProduced().forEach(t -> {
                CreditRatingState creditApplicationState = t.getState().getData();

                logger.info("Update in XYZ Node for Credit Application Id : " + creditApplicationState.getLoanVerificationId() + " Detected.");
                final CreditScoreDesc creditDesc = creditApplicationState.getCreditScoreDesc();
                final UniqueIdentifier creditApplicationId = creditApplicationState.getLoanVerificationId();

                logger.info("Credit Application Status description : " + creditDesc.toString());

                if (creditApplicationState.getCreditScoreDesc() != CreditScoreDesc.UNSPECIFIED) {
                    logger.info("Received Credit rating, updating the loan application, for credit check Id  : " + creditApplicationId.toString());
                    Thread newThreadCreditCheckFlow = new Thread(new UpdateLoanApplication(creditApplicationId, proxy, creditApplicationState.getCreditScoreDesc(),
                            creditApplicationState.getCreditScoreCheckRating()));
                    newThreadCreditCheckFlow.start();
                }

            }));


        } catch (Exception e) {
            logger.error("Exception occurred", e.getMessage());
            e.printStackTrace();
        }
    }
}

class InitiateCreditCheckFlow implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(InitiateCreditCheckFlow.class);

    private final UniqueIdentifier loanApplicationId;
    private final CordaRPCOps proxy;
    private final LoanApplicationStatus loanApplicationStatus;

    public InitiateCreditCheckFlow(UniqueIdentifier loanApplicationId, LoanApplicationStatus applicationStatus, CordaRPCOps proxy) {
        this.loanApplicationId = loanApplicationId;
        this.proxy = proxy;
        this.loanApplicationStatus = applicationStatus;
    }

    @Override
    public void run() {
        try {
            UniqueIdentifier creditCheckApplicationId = null;
            logger.info("Starting CreditScoreCheckFlow for Loan ApplicationID : " + loanApplicationId.getId().toString());

            Party creditCheckAgency = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=NewShireCreditRatingAgency,L=New York,C=US"));
            SignedTransaction tx = proxy.startTrackedFlowDynamic(CreditCheckInitiationFlow.class, loanApplicationId, creditCheckAgency).getReturnValue().get();
            creditCheckApplicationId = ((CreditRatingState) tx.getTx().getOutputs().get(0).getData()).getLoanVerificationId();
            logger.info("Credit Check flow initiated with CreditCheck Application Id : " + creditCheckApplicationId.toString());

            SignedTransaction loanUpdateTx = proxy.startTrackedFlowDynamic(LoanApplicationCreationFlow.class, loanApplicationStatus, loanApplicationId, creditCheckApplicationId).getReturnValue().get();
            logger.info("Application status for the Loan application is updated with Secure Hash : " + loanUpdateTx.getId().toString());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error while initiating CreditScoreCheckFlow for loanApplicationId : " + loanApplicationId.getId().toString());
            e.printStackTrace();
        }
    }
}


class UpdateLoanApplication implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(InitiateCreditCheckFlow.class);

    private final UniqueIdentifier creditApplicationId;
    private final CordaRPCOps proxy;
    private final CreditScoreDesc scoreDesc;
    private final Double creditScore;

    public UpdateLoanApplication(UniqueIdentifier creditApplicationId, CordaRPCOps proxy, CreditScoreDesc scoreDesc, Double creditScore) {
        this.creditApplicationId = creditApplicationId;
        this.proxy = proxy;
        this.scoreDesc = scoreDesc;
        this.creditScore = creditScore;
    }

    @Override
    public void run() {
        try {
            logger.info("Obtained Credit Score Rating from CreditAgency for credit application : " + creditApplicationId +
                    " scoreDesc : " + scoreDesc.toString() + " creditScore : " + creditScore);
            SignedTransaction loanUpdateTx = proxy.startTrackedFlowDynamic(LoanApplicationCreationFlow.class, creditApplicationId, scoreDesc, creditScore).getReturnValue().get();
            logger.info("Application status for the Loan application is updated with Secure Hash : " + loanUpdateTx.getId().toString());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error while initiating CreditScoreCheckFlow for loanApplicationId : " + creditApplicationId.getId().toString());
            e.printStackTrace();
        }
    }
}
