package com.xyz.observer.bank;

import com.xyz.processor.bank.BankProcessingProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xyz.constants.BankProcessingStatus;
import com.xyz.states.BankFinanceState;

import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.DataFeed;
import net.corda.core.node.services.Vault;
import rx.Observable;

public class BankLoanProcessingStateObserver {
	private static final Logger logger = LoggerFactory.getLogger(BankLoanProcessingStateObserver.class);

	private final CordaRPCOps proxy;

	public BankLoanProcessingStateObserver(CordaRPCOps proxy) {
		this.proxy = proxy;
	}

	public void observeBankProcessingRequest() {
		try {
			// Grab all existing and future states in the vault.
			final DataFeed<Vault.Page<BankFinanceState>, Vault.Update<BankFinanceState>> dataFeed = proxy
					.vaultTrack(BankFinanceState.class);
			final Observable<Vault.Update<BankFinanceState>> updates = dataFeed.getUpdates();

			updates.toBlocking().subscribe(update -> update.getProduced().forEach(t -> {
				BankFinanceState bankFinanaceState = t.getState().getData();

				if (bankFinanaceState.getBankProcessingStatus() == BankProcessingStatus.IN_PROCESSING) {
					logger.info("New Application for Bank loan processing from FA is detected with ID : "
							+ bankFinanaceState.getBankLoanProcessingId().toString());
					final UniqueIdentifier bankProcessingApplicationId = bankFinanaceState.getBankLoanProcessingId();

					logger.info("Initiating Bank Processing from observer for Bank Processing Application ID  : "
							+ bankProcessingApplicationId);
					new BankProcessingProcessor(bankProcessingApplicationId, proxy).processLoanDisbursement();
				}
			}));
		} catch (Exception e) {
			logger.error("Exception occurred", e.getMessage());
			e.printStackTrace();
		}
	}
}

