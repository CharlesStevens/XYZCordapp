package com.xyz.contracts;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

public class BankFinanceValidationContract implements Contract {
    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {

    }

    public interface Commands extends CommandData {
        public class BankProcessing implements BankFinanceValidationContract.Commands {
        }

        public class LoanDisbursed implements BankFinanceValidationContract.Commands {

        }
    }
}
