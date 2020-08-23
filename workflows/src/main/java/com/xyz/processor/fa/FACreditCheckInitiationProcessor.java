package com.xyz.processor.fa;

import com.xyz.constants.LoanApplicationStatus;
import com.xyz.flows.fa.CreditCheckInitiationFlow;
import com.xyz.flows.fa.LoanApplicationCreationFlow;
import com.xyz.states.CreditRatingState;
import com.xyz.states.LoanApplicationState;
import com.xyz.states.schema.LoaningProcessSchemas;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.services.vault.Builder;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.QueryCriteriaUtils;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class FACreditCheckInitiationProcessor {
    private static final Logger logger = LoggerFactory.getLogger(FACreditCheckInitiationProcessor.class);

    private final UniqueIdentifier loanApplicationId;
    private final CordaRPCOps proxy;

    public FACreditCheckInitiationProcessor(UniqueIdentifier loanApplicationId,
                                            CordaRPCOps proxy) {
        this.loanApplicationId = loanApplicationId;
        this.proxy = proxy;
    }

    public String processCreditCheckInitiation() {
        try {
            logger.info("Processing Credit Check initiation for Loan App ID : " + loanApplicationId.getId().toString());
            UniqueIdentifier creditCheckApplicationId = null;
            LoanApplicationStatus applicationStatus = fetchLoanApplicationStatus(loanApplicationId, proxy);
            logger.info("Current Loan Application status: " + applicationStatus + " for loan app id: " + loanApplicationId.getId().toString());

            if (applicationStatus == null)
                throw new IllegalStateException("Loan Application Id doesnt exists in the system");
            if (applicationStatus != LoanApplicationStatus.APPLIED)
                throw new IllegalStateException("Loan Application ID :" + loanApplicationId + " is not in its initial - APPLIED state");

            Party creditCheckAgency = proxy
                    .wellKnownPartyFromX500Name(CordaX500Name.parse("O=NewShireCreditRatingAgency,L=New York,C=US"));
            SignedTransaction tx = proxy
                    .startTrackedFlowDynamic(CreditCheckInitiationFlow.class, loanApplicationId, creditCheckAgency)
                    .getReturnValue().get();
            creditCheckApplicationId = ((CreditRatingState) tx.getTx().getOutputs().get(0).getData())
                    .getLoanVerificationId();
            logger.info("Credit Check flow initiated with CreditCheck Application Id : "
                    + creditCheckApplicationId.toString() + ". Updating loan application status...");

            SignedTransaction loanUpdateTx = proxy.startTrackedFlowDynamic(LoanApplicationCreationFlow.class,
                    LoanApplicationStatus.FORWARDED_TO_CREDIT_CHECK_AGENCY, loanApplicationId, creditCheckApplicationId).getReturnValue().get();
            LoanApplicationState laState = ((LoanApplicationState) loanUpdateTx.getTx().getOutputs().get(0).getData());
            logger.info("Updated Loan application ID: " + loanApplicationId.getId().toString() + " with status : " + LoanApplicationStatus.FORWARDED_TO_CREDIT_CHECK_AGENCY.toString());
            return "CreditCheck verification process initiated with VerificationID: " + creditCheckApplicationId.getId().toString();
        } catch (Exception e) {
            logger.error("Error while initiating CreditScoreCheckFlow for loanApplicationId : "
                    + loanApplicationId.getId().toString());
            e.printStackTrace();
            return "CreditCheck verification initiation process failed for Loan APP ID:  " + loanApplicationId.getId().toString() + ", ERROR: " + e.getMessage();
        }
    }

    private LoanApplicationStatus fetchLoanApplicationStatus(UniqueIdentifier loanApplicationId, CordaRPCOps proxy) throws IllegalStateException {
        QueryCriteria queryCriteria = null;
        List<StateAndRef<LoanApplicationState>> appState = null;
        try {
            queryCriteria = new QueryCriteria.VaultCustomQueryCriteria(
                    Builder.equal(QueryCriteriaUtils.getField("loanApplicationId",
                            LoaningProcessSchemas.PersistentLoanApplicationState.class), loanApplicationId.getId()));
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Field name bankProcessingId does not exist in PersistentLoanApplicationState class , " + e.getMessage());
        }
        appState = proxy.vaultQueryByCriteria(queryCriteria, LoanApplicationState.class).getStates();

        if (appState != null || !appState.isEmpty()) {
            return appState.get(0).getState().getData().getApplicationStatus();
        }
        return null;
    }
}
