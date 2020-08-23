package com.xyz.processor.fa;

import com.xyz.constants.BankProcessingStatus;
import com.xyz.flows.fa.LoanApplicationCreationFlow;
import com.xyz.states.BankFinanceState;
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

public class FAPostBankStatusUpdateProcessor {
    private static final Logger logger = LoggerFactory.getLogger(FAPostBankStatusUpdateProcessor.class);

    private UniqueIdentifier bankProcessingId;
    private UniqueIdentifier loanApplicationId;
    private final CordaRPCOps proxy;
    private BankProcessingStatus bankProcessingStatus;

    public FAPostBankStatusUpdateProcessor(UniqueIdentifier bankProcessingId, UniqueIdentifier loanApplicationId, CordaRPCOps proxy,
                                           BankProcessingStatus bankProcessingStatus) {
        this.bankProcessingId = bankProcessingId;
        this.loanApplicationId = loanApplicationId;
        this.proxy = proxy;
        this.bankProcessingStatus = bankProcessingStatus;
    }

    public String processBankFinanceProcessingUpdate() {
        try {
            if (bankProcessingId == null) {
                BankFinanceState bankFinanceState = fetchBankFinanceState(proxy, loanApplicationId);
                bankProcessingStatus = bankFinanceState.getBankProcessingStatus();
                bankProcessingId = bankFinanceState.getBankLoanProcessingId();
            }

            logger.info("Processing BankFinance processing Status : " + bankProcessingId.toString());
            SignedTransaction loanUpdateTx = proxy
                    .startTrackedFlowDynamic(LoanApplicationCreationFlow.class, bankProcessingId, bankProcessingStatus)
                    .getReturnValue().get();
            LoanApplicationState laState = ((LoanApplicationState) loanUpdateTx.getTx().getOutputs().get(0).getData());
            logger.info("Application status for the Loan application is Updated LoanApplication Id: "
                    + laState.getLoanApplicationId().toString() + " Verfication ID: "
                    + laState.getLoanVerificationId().toString() + " Status : "
                    + laState.getApplicationStatus().toString());
            return "Bank Finance State processed for bank finance Id: " + bankProcessingId.getId().toString();
        } catch (Exception e) {
            e.printStackTrace();
            String message = loanApplicationId == null ? " for Bank processing ID : " + bankProcessingId.getId().toString() :
                    " for LoanApplicationId : " + loanApplicationId.getId().toString();
            logger.error("BankFinanceUpdate processing Failed" + message + ", ERROR: " + e.getMessage());
            return "BankFinanceUpdate processing Failed" + message + ", ERROR: " + e.getMessage();
        }
    }

    private BankFinanceState fetchBankFinanceState(CordaRPCOps proxy, UniqueIdentifier loanApplicationId) throws
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

            UniqueIdentifier bankProcessingId = appState.getBankProcessingId();
            if (bankProcessingId == null) {
                throw new IllegalStateException("Bank processing may not have been initiated for Loan App Id :  " + loanApplicationId.getId().toString());
            }

            QueryCriteria bankProcessingCriteria = new QueryCriteria.VaultCustomQueryCriteria(
                    Builder.equal(QueryCriteriaUtils.getField("bankProcessingId",
                            LoaningProcessSchemas.PersistentBankProcessingSchema.class), bankProcessingId.getId()));
            List<StateAndRef<BankFinanceState>> stateAndRefBankFinanace = proxy.vaultQueryByCriteria(bankProcessingCriteria, BankFinanceState.class).getStates();
            if (stateAndRefBankFinanace == null || stateAndRefBankFinanace.isEmpty()) {
                throw new IllegalStateException("No BankProcessing ID :  " + bankProcessingId.getId().toString() + " found in the BankFinanceStates.");
            } else return stateAndRefBankFinanace.get(0).getState().getData();

        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Error while fetch BankFinanceState , " + e.getMessage());
        }
    }
}
