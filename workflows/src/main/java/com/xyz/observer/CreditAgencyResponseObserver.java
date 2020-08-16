package com.xyz.observer;

import com.xyz.constants.CreditScoreDesc;
import com.xyz.flows.LoanApplicationCreationFlow;
import com.xyz.states.CreditRatingState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.DataFeed;
import net.corda.core.node.services.Vault;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.util.concurrent.ExecutionException;

public class CreditAgencyResponseObserver {

    private static final Logger logger = LoggerFactory.getLogger(CreditAgencyResponseObserver.class);

    private final CordaRPCOps proxy;
    private final CordaX500Name me;

    public CreditAgencyResponseObserver(CordaRPCOps proxy, CordaX500Name name) {
        this.proxy = proxy;
        this.me = name;
    }

    public void observeCreditAgencyResponse() {
        try {
            final DataFeed<Vault.Page<CreditRatingState>, Vault.Update<CreditRatingState>> creditStateDataFeed = proxy.vaultTrack(CreditRatingState.class);
            final Observable<Vault.Update<CreditRatingState>> creditUpdates = creditStateDataFeed.getUpdates();

            creditUpdates.toBlocking().subscribe(update -> update.getProduced().forEach(t -> {
                CreditRatingState creditApplicationState = t.getState().getData();

                logger.info("Update in XYZ Node for Credit Application Id : " + creditApplicationState.getLoanVerificationId() + " Detected with CreditScoreDesc " +
                        creditApplicationState.getCreditScoreDesc().toString());
                final UniqueIdentifier creditApplicationId = creditApplicationState.getLoanVerificationId();

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
