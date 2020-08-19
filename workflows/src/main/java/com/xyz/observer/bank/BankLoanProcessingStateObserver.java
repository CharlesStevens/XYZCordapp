package com.xyz.observer.bank;

import com.xyz.constants.BankProcessingStatus;
import com.xyz.flows.bank.BankLoanDisbursementFlow;
import com.xyz.flows.ca.CreditCheckProcessingFlow;
import com.xyz.states.BankFinanceState;
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

public class BankLoanProcessingStateObserver {
    private static final Logger logger = LoggerFactory.getLogger(BankLoanProcessingStateObserver.class);

    private final CordaRPCOps proxy;
    private final CordaX500Name me;

    public BankLoanProcessingStateObserver(CordaRPCOps proxy, CordaX500Name name) {
        this.proxy = proxy;
        this.me = name;
    }

    public void observeBankProcessingRequest() {
        try {
            // Grab all existing and future states in the vault.
            final DataFeed<Vault.Page<BankFinanceState>, Vault.Update<BankFinanceState>> dataFeed = proxy.vaultTrack(BankFinanceState.class);
            final Observable<Vault.Update<BankFinanceState>> updates = dataFeed.getUpdates();

            updates.toBlocking().subscribe(update -> update.getProduced().forEach(t -> {
                BankFinanceState bankFinanaceState = t.getState().getData();

                if (bankFinanaceState.getBankProcessingStatus() == BankProcessingStatus.IN_PROCESSING) {
                    logger.info("New Application for Bank loan processing from FA is detected with ID : " + bankFinanaceState.getBankLoanProcessingId().toString());
                    final UniqueIdentifier bankProcessingApplicationId = bankFinanaceState.getBankLoanProcessingId();

                    logger.info("Initiating Bank Processing from observer for Bank Processing Application ID  : " + bankProcessingApplicationId);
                    new BankProcessingTrigger(bankProcessingApplicationId, proxy).trigger();
                }
            }));
        } catch (Exception e) {
            logger.error("Exception occurred", e.getMessage());
            e.printStackTrace();
        }
    }
}

class BankProcessingTrigger {
    private static final Logger logger = LoggerFactory.getLogger(BankProcessingTrigger.class);

    private final UniqueIdentifier bankProcessingApplicationId;
    private final CordaRPCOps proxy;

    public BankProcessingTrigger(UniqueIdentifier bankProcessingApplicationId, CordaRPCOps proxy) {
        this.bankProcessingApplicationId = bankProcessingApplicationId;
        this.proxy = proxy;
    }

    public void trigger() {
        try {
            logger.info("INTENTIONAL SLEEP OF 20 SEC, TO VERIFY STATES INVOKING APIs");
            Thread.sleep(20000);

            logger.info("Starting processing for bank processing for Processing ApplicationID : " + this.bankProcessingApplicationId.getId().toString());
            Party financeAgencyNode = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=XYZLoaning,L=London,C=GB"));

            SignedTransaction tx = proxy.startTrackedFlowDynamic(BankLoanDisbursementFlow.class, this.bankProcessingApplicationId, financeAgencyNode).getReturnValue().get();
            BankFinanceState finanaceState = ((BankFinanceState) tx.getTx().getOutputs().get(0).getData());

            logger.info("Bank Finance is processed for bank processing ID : " +
                    finanaceState.getBankLoanProcessingId().toString() + " With Status : " + finanaceState.getBankProcessingStatus().toString());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error while initiating CreditScoreCheckFlow for loanApplicationId : " + bankProcessingApplicationId.getId().toString());
            e.printStackTrace();
        }
    }
}
