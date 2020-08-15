package com.xyz.constants;

import net.corda.core.serialization.CordaSerializable;

@CordaSerializable
public enum LoanApplicationStatus {
    APPLIED, DENIED, APPROVED, DECISION_PENDING
}