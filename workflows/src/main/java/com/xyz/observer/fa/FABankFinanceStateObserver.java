package com.xyz.observer.fa;

import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xyz.constants.BankProcessingStatus;
import com.xyz.flows.fa.LoanApplicationCreationFlow;
import com.xyz.states.BankFinanceState;
import com.xyz.states.LoanApplicationState;

import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.DataFeed;
import net.corda.core.node.services.Vault;
import net.corda.core.transactions.SignedTransaction;
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
					new FABankFinanceProcessedTrigger(bankLoanApplicationId, proxy,
							bankFinanceState.getBankProcessingStatus()).trigger();
				}

			}));

		} catch (Exception e) {
			logger.error("Exception occurred", e.getMessage());
			e.printStackTrace();
		}
	}

}

class FABankFinanceProcessedTrigger {
	private static final Logger logger = LoggerFactory.getLogger(FABankFinanceProcessedTrigger.class);

	private final UniqueIdentifier bankProcessingId;
	private final CordaRPCOps proxy;
	private final BankProcessingStatus bankProcessingStatus;

	public FABankFinanceProcessedTrigger(UniqueIdentifier bankProcessingId, CordaRPCOps proxy,
			BankProcessingStatus bankProcessingStatus) {
		this.bankProcessingId = bankProcessingId;
		this.proxy = proxy;
		this.bankProcessingStatus = bankProcessingStatus;
	}

	public void trigger() {
		try {
			logger.info("Finalising the LoanApplication status for verification ID : " + bankProcessingId.toString());
			SignedTransaction loanUpdateTx = proxy
					.startTrackedFlowDynamic(LoanApplicationCreationFlow.class, bankProcessingId, bankProcessingStatus)
					.getReturnValue().get();
			LoanApplicationState laState = ((LoanApplicationState) loanUpdateTx.getTx().getOutputs().get(0).getData());
			logger.info("Application status for the Loan application is Updated LoanApplication Id: "
					+ laState.getLoanApplicationId().toString() + " Verfication ID: "
					+ laState.getLoanVerificationId().toString() + " Status : "
					+ laState.getApplicationStatus().toString());
		} catch (InterruptedException | ExecutionException e) {
			logger.error("Error while Processing BankProcessing Status for loanApplicationId : "
					+ bankProcessingId.getId().toString());
			e.printStackTrace();
		}
	}
}
