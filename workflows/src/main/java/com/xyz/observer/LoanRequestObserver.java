package com.xyz.observer;

import com.xyz.constants.LoanApplicationStatus;
import com.xyz.flows.CreditScoreCheckFlow;
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

    public void observe() {
        try {
            // Grab all existing and future states in the vault.
            final DataFeed<Vault.Page<LoanApplicationState>, Vault.Update<LoanApplicationState>> dataFeed = proxy.vaultTrack(LoanApplicationState.class);
            final Vault.Page<LoanApplicationState> snapshot = dataFeed.getSnapshot();
            final Observable<Vault.Update<LoanApplicationState>> updates = dataFeed.getUpdates();

            updates.toBlocking().subscribe(update -> update.getProduced().forEach(t -> {

                LoanApplicationState applicationState = t.getState().getData();
                logger.info("Update in XYZ Node for Loan Application Id : " + applicationState.getLoanApplicationId() + " Detected.");
                final LoanApplicationStatus applicationStatus = applicationState.getApplicationStatus();
                final UniqueIdentifier applicationId = applicationState.getLoanApplicationId();

                logger.info("loan Application status : " + applicationStatus);

                if (applicationStatus == LoanApplicationStatus.APPLIED) {
                    logger.info("Initiating Credit Check flow from observer for Loan Application ID  : " + applicationId);
                    Thread newThreadCreditCheckFlow = new Thread(new InitiateCreditCheckFlow(applicationId, proxy));
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

    public InitiateCreditCheckFlow(UniqueIdentifier loanApplicationId, CordaRPCOps proxy) {
        this.loanApplicationId = loanApplicationId;
        this.proxy = proxy;
    }

    @Override
    public void run() {
        try {
            logger.info("Starting CreditScoreCheckFlow for Loan ApplicationID : " + loanApplicationId.getId().toString());
            Party creditCheckAgency = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=NewShireCreditRatingAgency,L=New York,C=US"));
            SignedTransaction tx = proxy.startTrackedFlowDynamic(CreditScoreCheckFlow.class, loanApplicationId, creditCheckAgency).getReturnValue().get();
            logger.info("Credit Check flow initiated with CreditCheck Application Id : " + ((CreditRatingState) tx.getTx().getOutputs().get(0).getData()).getLoanVerificationId().toString());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error while initiating CreditScoreCheckFlow for loanApplicationId : " + loanApplicationId.getId().toString());
            e.printStackTrace();
        }
    }
}

