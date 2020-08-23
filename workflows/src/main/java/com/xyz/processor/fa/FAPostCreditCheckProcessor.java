package com.xyz.processor.fa;

import com.xyz.constants.CreditScoreDesc;
import com.xyz.flows.fa.LoanApplicationCreationFlow;
import com.xyz.states.CreditRatingState;
import com.xyz.states.LoanApplicationState;
import com.xyz.states.schema.LoaningProcessSchemas;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.services.vault.Builder;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.QueryCriteriaUtils;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FAPostCreditCheckProcessor {
    private static final Logger logger = LoggerFactory.getLogger(FAPostCreditCheckProcessor.class);

    private UniqueIdentifier creditApplicationId;
    private UniqueIdentifier loanApplicationId;
    private final CordaRPCOps proxy;
    private CreditScoreDesc scoreDesc;

    public FAPostCreditCheckProcessor(UniqueIdentifier creditApplicationId, UniqueIdentifier loanApplicationId, CordaRPCOps proxy,
                                      CreditScoreDesc scoreDesc) {
        this.creditApplicationId = creditApplicationId;
        this.loanApplicationId = loanApplicationId;
        this.proxy = proxy;
        this.scoreDesc = scoreDesc;
    }

    public String processCreditScores() {
        try {
            if (creditApplicationId == null) {
                CreditRatingState creditRatingState = fetchCreditVerificationID(proxy, loanApplicationId);
                scoreDesc = creditRatingState.getCreditScoreDesc();
                creditApplicationId = creditRatingState.getLoanVerificationId();
            }

            logger.info(
                    "Post Processing credit scores result for credit verification ID : " + creditApplicationId.toString());
            SignedTransaction loanUpdateTx = proxy
                    .startTrackedFlowDynamic(LoanApplicationCreationFlow.class, creditApplicationId, scoreDesc)
                    .getReturnValue().get();
            LoanApplicationState laState = ((LoanApplicationState) loanUpdateTx.getTx().getOutputs().get(0).getData());
            logger.info("Application status for the Loan application is Updated LoanApplication Id: "
                    + laState.getLoanApplicationId().toString() + " Verfication ID: "
                    + laState.getLoanVerificationId().toString() + " Status : "
                    + laState.getApplicationStatus().toString());
            return "Application Status Updated for Loan Verification Id : " + creditApplicationId.getId().toString();
        } catch (Exception e) {
            e.printStackTrace();
            String message = loanApplicationId == null ? " for verification ID : " + creditApplicationId.getId().toString() :
                    " for LoanApplicationId : " + loanApplicationId.getId().toString();
            logger.error("CreditScores processing Failed" + message + ", ERROR: " + e.getMessage());
            return "CreditScores processing Failed" + message + ", ERROR: " + e.getMessage();
        }
    }

    private CreditRatingState fetchCreditVerificationID(CordaRPCOps proxy, UniqueIdentifier loanApplicationId) throws
            IllegalStateException {
        QueryCriteria queryCriteria = null;
        LoanApplicationState appState = null;
        try {
            queryCriteria = new QueryCriteria.VaultCustomQueryCriteria(
                    Builder.equal(QueryCriteriaUtils.getField("loanApplicationId",
                            LoaningProcessSchemas.PersistentLoanApplicationState.class), loanApplicationId.getId()));
            List<StateAndRef<LoanApplicationState>> stateAndRefs = proxy.vaultQueryByCriteria(queryCriteria, LoanApplicationState.class).getStates();

            if (stateAndRefs == null || stateAndRefs.isEmpty()) {
                throw new IllegalStateException("No LoanAppId:  " + loanApplicationId.getId().toString() + " found in the systems.");
            } else
                appState = stateAndRefs.get(0).getState().getData();

            UniqueIdentifier verificationId = appState.getLoanVerificationId();
            if (verificationId == null) {
                throw new IllegalStateException("Loan Verification process may not have been initiated for Loan App Id :  " + loanApplicationId.getId().toString());
            }

            QueryCriteria creditStateCriteria = new QueryCriteria.VaultCustomQueryCriteria(
                    Builder.equal(QueryCriteriaUtils.getField("loanVerificationId",
                            LoaningProcessSchemas.PersistentCreditRatingSchema.class), verificationId.getId()));
            List<StateAndRef<CreditRatingState>> stateAndRefCreditStates = proxy.vaultQueryByCriteria(creditStateCriteria, CreditRatingState.class).getStates();
            if (stateAndRefCreditStates == null || stateAndRefCreditStates.isEmpty()) {
                throw new IllegalStateException("No Verification ID :  " + verificationId.getId().toString() + " found in the CreditRatingState.");
            } else return stateAndRefCreditStates.get(0).getState().getData();

        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Error while fetching CreditRatingStates , " + e.getMessage());
        }
    }
}

