package com.xyz.observer.fa;

import com.xyz.constants.LoanApplicationStatus;
import com.xyz.processor.fa.FABankProcessInitiationProcessor;
import com.xyz.processor.fa.FACreditCheckInitiationProcessor;
import com.xyz.states.LoanApplicationState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.DataFeed;
import net.corda.core.node.services.Vault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

/**
 * This observer observes a event of new Loan Application Creation and initiates
 * a LoanVerfication transaction with the CA. Post which it updates the status
 * of the Loan application to Decision_pending.
 */
public class FALoanApplicationStateObserver {
    private static final Logger logger = LoggerFactory.getLogger(FALoanApplicationStateObserver.class);

    private final CordaRPCOps proxy;

    public FALoanApplicationStateObserver(CordaRPCOps proxy) {
        this.proxy = proxy;
    }

    public void observeLoanApplicationUpdate() {
        try {
            final DataFeed<Vault.Page<LoanApplicationState>, Vault.Update<LoanApplicationState>> loanStateDataFeed = proxy
                    .vaultTrack(LoanApplicationState.class);
            final Observable<Vault.Update<LoanApplicationState>> loanUpdates = loanStateDataFeed.getUpdates();

            loanUpdates.toBlocking().subscribe(update -> update.getProduced().forEach(t -> {

                LoanApplicationState applicationState = t.getState().getData();
                final LoanApplicationStatus applicationStatus = applicationState.getApplicationStatus();
                final UniqueIdentifier applicationId = applicationState.getLoanApplicationId();
                logger.info("LoanApplicationState Update Observed for : " + applicationId.toString() + " and Status : "
                        + applicationStatus.toString());

                if (applicationStatus == LoanApplicationStatus.APPLIED) {
                    new FACreditCheckInitiationProcessor(applicationId, proxy).processCreditCheckInitiation();
                } else if (applicationStatus == LoanApplicationStatus.CREDIT_SCORE_CHECK_PASS) {
                    new FABankProcessInitiationProcessor(applicationId, proxy).processBankInitiation();
                }
            }));

        } catch (Exception e) {
            logger.error("Exception occurred", e.getMessage());
            e.printStackTrace();
        }
    }
}

