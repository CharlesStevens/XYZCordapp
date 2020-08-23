package com.xyz.processor.fa;


import com.xyz.flows.fa.BankLoanProcessingInitiationFlow;
import com.xyz.flows.fa.LoanApplicationCreationFlow;
import com.xyz.states.BankFinanceState;
import com.xyz.states.LoanApplicationState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FABankProcessInitiationProcessor {
    private static final Logger logger = LoggerFactory.getLogger(FABankProcessInitiationProcessor.class);

    private final UniqueIdentifier loanApplicationID;
    private final CordaRPCOps proxy;

    public FABankProcessInitiationProcessor(UniqueIdentifier loanApplicationId, CordaRPCOps proxy) {
        this.loanApplicationID = loanApplicationId;
        this.proxy = proxy;
    }

    public String processBankInitiation() {
        try {
            logger.info("Initiating Bank processing for LoanApplicationID : " + loanApplicationID.toString());

            Party bankNode = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=MTCBank,L=New York,C=US"));
            SignedTransaction bankProcessing = proxy
                    .startTrackedFlowDynamic(BankLoanProcessingInitiationFlow.class, loanApplicationID, bankNode)
                    .getReturnValue().get();
            BankFinanceState bankState = ((BankFinanceState) bankProcessing.getTx().getOutputs().get(0).getData());
            logger.info("Loan processing initiated with Bank with Bank Application ID : "
                    + bankState.getBankLoanProcessingId().toString() + " with status : "
                    + bankState.getBankProcessingStatus().toString());

            SignedTransaction loanUpdateTx = proxy.startTrackedFlowDynamic(LoanApplicationCreationFlow.class,
                    bankState.getBankLoanProcessingId(), loanApplicationID)
                    .getReturnValue().get();
            LoanApplicationState laState = ((LoanApplicationState) loanUpdateTx.getTx().getOutputs().get(0).getData());
            logger.info("Processed Bank Process initiation with Bank processing ID: " + bankState.getBankLoanProcessingId().getId().toString());
            return "Bank processing initated with Bank processing ID : " + bankState.getBankLoanProcessingId().getId().toString();
        } catch (Exception e) {
            logger.error("Error while initiating Bank Processing Initiation for loanApplicationId : "
                    + loanApplicationID.getId().toString());
            e.printStackTrace();
            return "Error while initiating Bank Processing Initiation for loanApplicationId : "
                    + loanApplicationID.getId().toString();
        }
    }
}