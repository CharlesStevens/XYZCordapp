package com.xyz.contracts;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

public class LoanApplicationContract implements Contract {


    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {

    }

    public interface Commands extends CommandData {
        public class LoanApplied implements Commands {
        }

        public class LoanProcesedForCreditCheck implements Commands {

        }

        public class LoanApplicationResponse implements Commands {
        }
    }

}