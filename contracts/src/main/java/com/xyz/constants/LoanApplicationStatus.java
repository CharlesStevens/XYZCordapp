package com.xyz.constants;

import net.corda.core.serialization.CordaSerializable;

@CordaSerializable
public enum LoanApplicationStatus {
    APPLIED("APPLIED"),
    FORWARDED_TO_CREDIT_CHECK_AGENCY("DECISION_PENDING"),
    CREDIT_SCORE_CHECK_FAILED("REJECTED"),
    CREDIT_SCORE_CHECK_PASS("PROCESSING"),
    FORWARDED_TO_BANK("PROCESSING"),
    REJECTED_FROM_BANK("REJECTED"),
    LOAN_DISBURSED("PROCESSED");

    final String asbtractLoanProcessingStatus;

    private LoanApplicationStatus(String abstractStatus) {
        this.asbtractLoanProcessingStatus = abstractStatus;
    }

    public String getAbstractProcessingStatus() {
        return this.asbtractLoanProcessingStatus;
    }

}