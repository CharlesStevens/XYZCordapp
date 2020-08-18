package com.xyz.constants;

import net.corda.core.serialization.CordaSerializable;

@CordaSerializable
public enum BankProcessingStatus {
    IN_PROCESSING, REJECTED, PROCESSED
}
