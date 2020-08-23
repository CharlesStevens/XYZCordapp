package com.xyz.observer.fa;

import com.xyz.constants.CreditScoreDesc;
import com.xyz.processor.fa.FAPostCreditCheckProcessor;
import com.xyz.states.CreditRatingState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.DataFeed;
import net.corda.core.node.services.Vault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

public class FACreditScoreCheckStateObserver {

    private static final Logger logger = LoggerFactory.getLogger(FACreditScoreCheckStateObserver.class);

    private final CordaRPCOps proxy;

    public FACreditScoreCheckStateObserver(CordaRPCOps proxy) {
        this.proxy = proxy;
    }

    public void observeCreditAgencyResponse() {
        try {
            final DataFeed<Vault.Page<CreditRatingState>, Vault.Update<CreditRatingState>> creditStateDataFeed = proxy
                    .vaultTrack(CreditRatingState.class);
            final Observable<Vault.Update<CreditRatingState>> creditUpdates = creditStateDataFeed.getUpdates();

            creditUpdates.toBlocking().subscribe(update -> update.getProduced().forEach(t -> {
                CreditRatingState creditApplicationState = t.getState().getData();

                logger.info("Update in CreditRatingState detected for CreditCheck verification Id : "
                        + creditApplicationState.getLoanVerificationId() + " with CreditScoreDesc "
                        + creditApplicationState.getCreditScoreDesc().toString());
                final UniqueIdentifier creditApplicationId = creditApplicationState.getLoanVerificationId();

                if (creditApplicationState.getCreditScoreDesc() != CreditScoreDesc.UNSPECIFIED) {
                    logger.info("CreditScore check has been completed from Credit Check agency, for verification ID : "
                            + creditApplicationId.toString() + " with CreditScore rating : "
                            + creditApplicationState.getCreditScoreCheckRating() + " and credit score desc : "
                            + creditApplicationState.getCreditScoreDesc().toString());
                    new FAPostCreditCheckProcessor(creditApplicationId, null, proxy,
                            creditApplicationState.getCreditScoreDesc()).processCreditScores();
                }

            }));

        } catch (Exception e) {
            logger.error("Exception occurred", e.getMessage());
            e.printStackTrace();
        }
    }

}

