package com.xyz.observer;

import com.xyz.flows.CreditCheckInitiationFlow;
import com.xyz.flows.CreditCheckProcessingFlow;
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

public class CreditCheckInitiationObserver {
    private static final Logger logger = LoggerFactory.getLogger(CreditCheckInitiationObserver.class);

    private final CordaRPCOps proxy;
    private final CordaX500Name me;

    public CreditCheckInitiationObserver(CordaRPCOps proxy, CordaX500Name name) {
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
                logger.info("New Application for Credit Check from FA is detected with ID : " + creditCheckState.getLoanVerificationId().toString());
                final UniqueIdentifier creditCheckApplicationId = creditCheckState.getLoanVerificationId();

                logger.info("Initiating Credit Check flow from observer for CreditCheck Application ID  : " + creditCheckApplicationId);
                Thread newThreadCreditCheckFlow = new Thread(new InitiateCreditCheckProcess(creditCheckApplicationId, proxy));
                newThreadCreditCheckFlow.start();

            }));
        } catch (Exception e) {
            logger.error("Exception occurred", e.getMessage());
            e.printStackTrace();
        }
    }
}

class InitiateCreditCheckProcess implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(InitiateCreditCheckProcess.class);

    private final UniqueIdentifier creditCheckApplicationId;
    private final CordaRPCOps proxy;

    public InitiateCreditCheckProcess(UniqueIdentifier creditCheckApplicationId, CordaRPCOps proxy) {
        this.creditCheckApplicationId = creditCheckApplicationId;
        this.proxy = proxy;
    }

    @Override
    public void run() {
        try {
            logger.info("INitiating the credit check flow but sleeping for 2 minutes from now");
            Thread.sleep(120000);
            UniqueIdentifier creditCheckApplicationId = null;
            logger.info("Starting CreditScoreCheckFlow for Loan ApplicationID : " + this.creditCheckApplicationId.getId().toString());

            Party financeAgencyNode = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=XYZLoaning,L=London,C=GB"));
            SignedTransaction tx = proxy.startTrackedFlowDynamic(CreditCheckProcessingFlow.class, this.creditCheckApplicationId, financeAgencyNode).getReturnValue().get();
            creditCheckApplicationId = ((CreditRatingState) tx.getTx().getOutputs().get(0).getData()).getLoanVerificationId();
            logger.info("Credit Check flow updated with CreditCheck Application Id : " + creditCheckApplicationId.toString());

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error while initiating CreditScoreCheckFlow for loanApplicationId : " + creditCheckApplicationId.getId().toString());
            e.printStackTrace();
        }
    }
}


