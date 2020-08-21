package com.xyz.observer.fa;

import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xyz.constants.CreditScoreDesc;
import com.xyz.flows.fa.LoanApplicationCreationFlow;
import com.xyz.states.CreditRatingState;
import com.xyz.states.LoanApplicationState;

import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.DataFeed;
import net.corda.core.node.services.Vault;
import net.corda.core.transactions.SignedTransaction;
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
					new FACreditScoreCheckStateTrigger(creditApplicationId, proxy,
							creditApplicationState.getCreditScoreDesc()).trigger();
				}

			}));

		} catch (Exception e) {
			logger.error("Exception occurred", e.getMessage());
			e.printStackTrace();
		}
	}

}

class FACreditScoreCheckStateTrigger {
	private static final Logger logger = LoggerFactory.getLogger(FACreditScoreCheckStateTrigger.class);

	private final UniqueIdentifier creditApplicationId;
	private final CordaRPCOps proxy;
	private final CreditScoreDesc scoreDesc;

	public FACreditScoreCheckStateTrigger(UniqueIdentifier creditApplicationId, CordaRPCOps proxy,
			CreditScoreDesc scoreDesc) {
		this.creditApplicationId = creditApplicationId;
		this.proxy = proxy;
		this.scoreDesc = scoreDesc;
	}

	public void trigger() {
		try {
			logger.info(
					"Finalising the LoanApplication status for verification ID : " + creditApplicationId.toString());
			SignedTransaction loanUpdateTx = proxy
					.startTrackedFlowDynamic(LoanApplicationCreationFlow.class, creditApplicationId, scoreDesc)
					.getReturnValue().get();
			LoanApplicationState laState = ((LoanApplicationState) loanUpdateTx.getTx().getOutputs().get(0).getData());
			logger.info("Application status for the Loan application is Updated LoanApplication Id: "
					+ laState.getLoanApplicationId().toString() + " Verfication ID: "
					+ laState.getLoanVerificationId().toString() + " Status : "
					+ laState.getApplicationStatus().toString());
		} catch (InterruptedException | ExecutionException e) {
			logger.error("Error while initiating CreditScoreCheckFlow for loanApplicationId : "
					+ creditApplicationId.getId().toString());
			e.printStackTrace();
		}
	}
}
