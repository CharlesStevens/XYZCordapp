package com.xyz.processor.bank;

import com.xyz.flows.bank.BankLoanDisbursementFlow;
import com.xyz.states.BankFinanceState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BankProcessingProcessor {
    private static final Logger logger = LoggerFactory.getLogger(BankProcessingProcessor.class);

    private final UniqueIdentifier bankProcessingApplicationId;
    private final CordaRPCOps proxy;

    public BankProcessingProcessor(UniqueIdentifier bankProcessingApplicationId, CordaRPCOps proxy) {
        this.bankProcessingApplicationId = bankProcessingApplicationId;
        this.proxy = proxy;
    }

    public String processLoanDisbursement() {
        try {
            logger.info("Starting processing for bank processing for Processing ApplicationID : "
                    + this.bankProcessingApplicationId.getId().toString());
            Party financeAgencyNode = proxy
                    .wellKnownPartyFromX500Name(CordaX500Name.parse("O=XYZLoaning,L=London,C=GB"));

            SignedTransaction tx = proxy.startTrackedFlowDynamic(BankLoanDisbursementFlow.class,
                    this.bankProcessingApplicationId, financeAgencyNode).getReturnValue().get();
            BankFinanceState finanaceState = ((BankFinanceState) tx.getTx().getOutputs().get(0).getData());

            logger.info("Bank Finance is processed for bank processing ID : "
                    + finanaceState.getBankLoanProcessingId().toString() + " With Status : "
                    + finanaceState.getBankProcessingStatus().toString());
            return "Bank Loan processing completed for bank processing ID: " + bankProcessingApplicationId.getId().toString();
        } catch (Exception e) {
            logger.error("Error while processing bank loan for bank processing ID : "
                    + bankProcessingApplicationId.getId().toString());
            e.printStackTrace();
            return "Error while processing bank loan for bank processing ID : "
                    + bankProcessingApplicationId.getId().toString();
        }
    }
}