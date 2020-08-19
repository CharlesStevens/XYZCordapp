package com.xyz.contracts;

import com.xyz.constants.CreditScoreDesc;
import com.xyz.states.CreditRatingState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class CreditRatingCheckContract implements Contract {

    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {
        if (tx != null && tx.getCommands().size() != 1)
            throw new IllegalArgumentException("More than one command for Single Transaction : INVALID");

        Command command = tx.getCommand(0);
        List<PublicKey> requiredSigners = command.getSigners();
        CommandData commandType = command.getValue();

        if (commandType instanceof CreditRatingCheckContract.Commands.CreditCheckInitiation) {
            verifyCreditCheckInitiation(tx, requiredSigners);
        } else if (commandType instanceof CreditRatingCheckContract.Commands.CreditCheckProcessed) {
            verifyCreditCheckProcessed(tx, requiredSigners);
        }
    }

    private void verifyCreditCheckInitiation(LedgerTransaction tx, List<PublicKey> requiredSigners) {
        requireThat(req -> {
            req.using("Two transaction signer expected", requiredSigners.size() == 2);
            req.using("Only one output should be created during the process CreditRatingState", tx.getOutputStates().size() == 1);

            req.using("Output state shall be of type CreditRatingState", tx.getOutput(0) instanceof CreditRatingState);
            req.using("No input should be consumed while initiating loan verification", tx.getInputStates().isEmpty());

            CreditRatingState creditRatingState = (CreditRatingState) tx.getOutput(0);

            req.using("Finanace agency signature not present in the transaction", requiredSigners.contains(creditRatingState.getLoaningAgency().getOwningKey()));
            req.using("Credit agency signature not present in the transaction", requiredSigners.contains(creditRatingState.getCreditAgencyNode().getOwningKey()));
            req.using("Initial credit score shall be 0 ", creditRatingState.getCreditScoreCheckRating().equals(0.0));
            req.using("Initial credit description shall be UNSPECIFIED", creditRatingState.getCreditScoreDesc() == CreditScoreDesc.UNSPECIFIED);
            req.using("Minimum Loan amount Validation failed", creditRatingState.getLoanAmount() > 0);
            req.using("Borrowing Company name cant be Empty", creditRatingState.getCompanyName() != null || !creditRatingState.getCompanyName().equals(""));
            req.using("Borrowing Company`s Business Type cant be Empty", creditRatingState.getBusinessType() != null || !creditRatingState.getBusinessType().equals(""));
            return null;
        });
    }

    private void verifyCreditCheckProcessed(LedgerTransaction tx, List<PublicKey> requiredSigners) {
        requireThat(req -> {
            req.using("Two transaction signer expected", requiredSigners.size() == 2);
            req.using("Only one output should be created during the process CreditRatingState", tx.getOutputStates().size() == 1);

            req.using("Output state shall be of type CreditRatingState", tx.getOutput(0) instanceof CreditRatingState);
            req.using("Input state shall be of type CreditRatingState", tx.getInput(0) instanceof CreditRatingState);

            CreditRatingState inC = (CreditRatingState) tx.getInput(0);
            CreditRatingState opC = (CreditRatingState) tx.getOutput(0);

            req.using("Finanace agency signature not present in the transaction", requiredSigners.contains(opC.getLoaningAgency().getOwningKey()));
            req.using("Credit agency signature not present in the transaction", requiredSigners.contains(opC.getCreditAgencyNode().getOwningKey()));
            req.using("Input credit score shall be 0 ", inC.getCreditScoreCheckRating().equals(0.0));
            req.using("Input credit description shall be UNSPECIFIED", inC.getCreditScoreDesc() == CreditScoreDesc.UNSPECIFIED);
            req.using("Credit Score and Credit Desc shall be not on initial state", (opC.getCreditScoreDesc() == CreditScoreDesc.FAIR ||
                    opC.getCreditScoreDesc() == CreditScoreDesc.GOOD || opC.getCreditScoreDesc() == CreditScoreDesc.POOR) && opC.getCreditScoreCheckRating() > 0.1);
            return null;
        });
    }

    public interface Commands extends CommandData {
        public class CreditCheckInitiation implements CreditRatingCheckContract.Commands {
        }

        public class CreditCheckProcessed implements CreditRatingCheckContract.Commands {
        }
    }
}