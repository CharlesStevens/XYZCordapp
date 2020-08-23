package com.xyz.processor.ca;

import com.xyz.flows.ca.CreditCheckProcessingFlow;
import com.xyz.states.CreditRatingState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CACreditScoreCheckProcessor {
    private static final Logger logger = LoggerFactory.getLogger(CACreditScoreCheckProcessor.class);

    private final UniqueIdentifier creditCheckApplicationId;
    private final CordaRPCOps proxy;

    public CACreditScoreCheckProcessor(UniqueIdentifier creditCheckApplicationId, CordaRPCOps proxy) {
        this.creditCheckApplicationId = creditCheckApplicationId;
        this.proxy = proxy;
    }

    public String processCreditScoreCheck() {
        try {
            logger.info("Starting processing for CreditScore evaluation for CreditCheck ApplicationID : "
                    + this.creditCheckApplicationId.getId().toString());
            Party financeAgencyNode = proxy
                    .wellKnownPartyFromX500Name(CordaX500Name.parse("O=XYZLoaning,L=London,C=GB"));

            SignedTransaction tx = proxy.startTrackedFlowDynamic(CreditCheckProcessingFlow.class,
                    this.creditCheckApplicationId, financeAgencyNode).getReturnValue().get();
            CreditRatingState caState = ((CreditRatingState) tx.getTx().getOutputs().get(0).getData());

            logger.info("Credit Check flow updated with CreditCheck Application Id : "
                    + caState.getLoanVerificationId().toString() + " With Status : "
                    + caState.getCreditScoreDesc().toString() + " and Score : " + caState.getCreditScoreCheckRating());
            return "Credit Score generated for Credit Application ID: " + creditCheckApplicationId.getId().toString();
        } catch (Exception e) {
            logger.error("Error while initiating CreditScoreCheckFlow for credit verificationID : "
                    + creditCheckApplicationId.getId().toString());
            e.printStackTrace();
            return "Error while initiating CreditScoreCheckFlow for credit verificationID : "
                    + creditCheckApplicationId.getId().toString();
        }
    }
}
