package com.xyz.contracts;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

public class CreditRatingCheckContract implements Contract {

    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {

    }

    public interface Commands extends CommandData {
        public class CreditCheckInitiation implements CreditRatingCheckContract.Commands {

        }

        public class CreditCheckProcessing implements CreditRatingCheckContract.Commands {
        }

        public class CreditCheckResponse implements CreditRatingCheckContract.Commands {
        }
    }

}