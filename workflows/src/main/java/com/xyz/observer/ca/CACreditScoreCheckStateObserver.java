package com.xyz.observer.ca;

import com.xyz.constants.CreditScoreDesc;
import com.xyz.flows.ca.CreditCheckProcessingFlow;
import com.xyz.states.CreditRatingState;
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

public class CACreditScoreCheckStateObserver {
    private static final Logger logger = LoggerFactory.getLogger(CACreditScoreCheckStateObserver.class);

    private final CordaRPCOps proxy;
    private final CordaX500Name me;

    public CACreditScoreCheckStateObserver(CordaRPCOps proxy, CordaX500Name name) {
        this.proxy = proxy;
        this.me = name;
    }

    public void observeCreditCheckApplication() {
        try {
            // Grab all existing and future states in the vault.
            final DataFeed<Vault.Page<CreditRatingState>, Vault.Update<CreditRatingState>> dataFeed = proxy.vaultTrack(CreditRatingState.class);
            final Observable<Vault.Update<CreditRatingState>> updates = dataFeed.getUpdates();

            updates.toBlocking().subscribe(update -> update.getProduced().forEach(t -> {
                CreditRatingState creditCheckState = t.getState().getData();

                if (creditCheckState.getCreditScoreDesc() == CreditScoreDesc.UNSPECIFIED) {
                    logger.info("New Application for Credit Check from FA is detected with ID : " + creditCheckState.getLoanVerificationId().toString());
                    final UniqueIdentifier creditCheckApplicationId = creditCheckState.getLoanVerificationId();

                    logger.info("Initiating Credit Check flow from observer for CreditCheck Application ID  : " + creditCheckApplicationId);
                    new CACreditScoreCheckStateTrigger(creditCheckApplicationId, proxy).trigger();
                }
            }));
        } catch (Exception e) {
            logger.error("Exception occurred", e.getMessage());
            e.printStackTrace();
        }
    }
}

class CACreditScoreCheckStateTrigger {
    private static final Logger logger = LoggerFactory.getLogger(CACreditScoreCheckStateTrigger.class);

    private final UniqueIdentifier creditCheckApplicationId;
    private final CordaRPCOps proxy;

    public CACreditScoreCheckStateTrigger(UniqueIdentifier creditCheckApplicationId, CordaRPCOps proxy) {
        this.creditCheckApplicationId = creditCheckApplicationId;
        this.proxy = proxy;
    }

    public void trigger() {
        try {
            logger.info("INTENTIONAL SLEEP OF 20 SEC, TO VERIFY STATES INVOKING APIs");
            Thread.sleep(20000);
            logger.info("Starting processing for CreditScore evaluation for CreditCheck ApplicationID : " + this.creditCheckApplicationId.getId().toString());
            Party financeAgencyNode = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=XYZLoaning,L=London,C=GB"));

            SignedTransaction tx = proxy.startTrackedFlowDynamic(CreditCheckProcessingFlow.class, this.creditCheckApplicationId, financeAgencyNode).getReturnValue().get();
            CreditRatingState caState = ((CreditRatingState) tx.getTx().getOutputs().get(0).getData());

            logger.info("Credit Check flow updated with CreditCheck Application Id : " +
                    caState.getLoanVerificationId().toString() + " With Status : " + caState.getCreditScoreDesc().toString() + " and Score : " + caState.getCreditScoreCheckRating());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error while initiating CreditScoreCheckFlow for loanApplicationId : " + creditCheckApplicationId.getId().toString());
            e.printStackTrace();
        }
    }
}


