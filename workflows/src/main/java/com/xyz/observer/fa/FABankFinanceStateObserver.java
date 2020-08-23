package com.xyz.observer.fa;

import com.xyz.constants.BankProcessingStatus;
import com.xyz.processor.fa.FAPostBankStatusUpdateProcessor;
import com.xyz.states.BankFinanceState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.DataFeed;
import net.corda.core.node.services.Vault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

public class FABankFinanceStateObserver {

    private static final Logger logger = LoggerFactory.getLogger(FABankFinanceStateObserver.class);

    private final CordaRPCOps proxy;

    public FABankFinanceStateObserver(CordaRPCOps proxy) {
        this.proxy = proxy;
    }

    public void observeBankFinanceState() {
        try {
            final DataFeed<Vault.Page<BankFinanceState>, Vault.Update<BankFinanceState>> bankStateFeed = proxy
                    .vaultTrack(BankFinanceState.class);
            final Observable<Vault.Update<BankFinanceState>> creditUpdates = bankStateFeed.getUpdates();

            creditUpdates.toBlocking().subscribe(update -> update.getProduced().forEach(t -> {
                BankFinanceState bankFinanceState = t.getState().getData();

                logger.info("Update in BankFinanceState detected for Bank Processing Id : "
                        + bankFinanceState.getBankLoanProcessingId() + " with processing status : "
                        + bankFinanceState.getBankProcessingStatus().toString());
                final UniqueIdentifier bankLoanApplicationId = bankFinanceState.getBankLoanProcessingId();

                if (bankFinanceState.getBankProcessingStatus() != BankProcessingStatus.IN_PROCESSING) {
                    new FAPostBankStatusUpdateProcessor(bankLoanApplicationId, null, proxy,
                            bankFinanceState.getBankProcessingStatus()).processBankFinanceProcessingUpdate();
                }

            }));

        } catch (Exception e) {
            logger.error("Exception occurred", e.getMessage());
            e.printStackTrace();
        }
    }

}

