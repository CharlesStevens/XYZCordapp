package com.xyz.observer.ca;

import com.xyz.constants.CreditScoreDesc;
import com.xyz.processor.ca.CACreditScoreCheckProcessor;
import com.xyz.states.CreditRatingState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.DataFeed;
import net.corda.core.node.services.Vault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

public class CACreditScoreCheckStateObserver {
	private static final Logger logger = LoggerFactory.getLogger(CACreditScoreCheckStateObserver.class);

	private final CordaRPCOps proxy;

	public CACreditScoreCheckStateObserver(CordaRPCOps proxy) {
		this.proxy = proxy;
	}

	public void observeCreditCheckApplication() {
		try {
			// Grab all existing and future states in the vault.
			final DataFeed<Vault.Page<CreditRatingState>, Vault.Update<CreditRatingState>> dataFeed = proxy
					.vaultTrack(CreditRatingState.class);
			final Observable<Vault.Update<CreditRatingState>> updates = dataFeed.getUpdates();

			updates.toBlocking().subscribe(update -> update.getProduced().forEach(t -> {
				CreditRatingState creditCheckState = t.getState().getData();

				if (creditCheckState.getCreditScoreDesc() == CreditScoreDesc.UNSPECIFIED) {
					logger.info("New Application for Credit Check from FA is detected with ID : "
							+ creditCheckState.getLoanVerificationId().toString());
					final UniqueIdentifier creditCheckApplicationId = creditCheckState.getLoanVerificationId();

					logger.info("Initiating Credit Check flow from observer for CreditCheck Application ID  : "
							+ creditCheckApplicationId);
					new CACreditScoreCheckProcessor(creditCheckApplicationId, proxy).processCreditScoreCheck();
				}
			}));
		} catch (Exception e) {
			logger.error("Exception occurred", e.getMessage());
			e.printStackTrace();
		}
	}
}

