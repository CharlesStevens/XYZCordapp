package com.xyz.constants;

import net.corda.core.serialization.CordaSerializable;

@CordaSerializable
public enum LoanDisbursalStatus {

    APPROVED_FROM_FINANCE_AGENCY, DENIED_TO_ADVANCE, DISBURSED_TO_BORROWER
}
