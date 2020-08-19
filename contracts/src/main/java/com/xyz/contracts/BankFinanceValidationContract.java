package com.xyz.contracts;

import com.xyz.constants.BankProcessingStatus;
import com.xyz.constants.CreditScoreDesc;
import com.xyz.states.BankFinanceState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class BankFinanceValidationContract implements Contract {
    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {
        if (tx != null && tx.getCommands().size() != 1)
            throw new IllegalArgumentException("More than one command for Single Transaction : INVALID");

        Command command = tx.getCommand(0);
        List<PublicKey> requiredSigners = command.getSigners();
        CommandData commandType = command.getValue();

        if (commandType instanceof BankFinanceValidationContract.Commands.BankProcessingInitiated) {
            verifyBankInitiation(tx, requiredSigners);
        } else if (commandType instanceof BankFinanceValidationContract.Commands.LoanRequestProcessed) {
            verifyBankProcessing(tx, requiredSigners);
        }
    }

    private void verifyBankInitiation(LedgerTransaction tx, List<PublicKey> requiredSigners) {
        requireThat(req -> {
            req.using("Two transaction signer expected", requiredSigners.size() == 2);
            req.using("Only one output should be created during the process BankFinanceState", tx.getOutputStates().size() == 1);

            req.using("Output state shall be of type BankFinanceState", tx.getOutput(0) instanceof BankFinanceState);
            req.using("No input should be consumed while initiating Bank processing", tx.getInputStates().isEmpty());

            BankFinanceState bankFinanceState = (BankFinanceState) tx.getOutput(0);

            req.using("Finance agency signature not present in the transaction", requiredSigners.contains(bankFinanceState.getFinanceAgencyNode().getOwningKey()));
            req.using("Bank signature not present in the transaction", requiredSigners.contains(bankFinanceState.getBankNode().getOwningKey()));
            req.using("Initial credit description shall be UNSPECIFIED or POOR", bankFinanceState.getCreditScoreDesc() != CreditScoreDesc.UNSPECIFIED || bankFinanceState.getCreditScoreDesc() != CreditScoreDesc.POOR);
            req.using("Initial processing status shall be IN_PROCESSING", bankFinanceState.getBankProcessingStatus() == BankProcessingStatus.IN_PROCESSING);
            req.using("Minimum Loan amount Validation failed", bankFinanceState.getLoanAmount() > 0);
            req.using("Borrowing Company name cant be Empty", bankFinanceState.getCompanyName() != null || !bankFinanceState.getCompanyName().equals(""));
            req.using("Borrowing Company`s Business Type cant be Empty", bankFinanceState.getBusinessType() != null || !bankFinanceState.getBusinessType().equals(""));
            return null;
        });
    }

    private void verifyBankProcessing(LedgerTransaction tx, List<PublicKey> requiredSigners) {
        requireThat(req -> {
            req.using("Two transaction signer expected", requiredSigners.size() == 2);
            req.using("Only one output should be created during the process BankFinanceState", tx.getOutputStates().size() == 1);

            req.using("Output state shall be of type BankFinanceState", tx.getOutput(0) instanceof BankFinanceState);
            req.using("Input state shall be of type BankFinanceState", tx.getInput(0) instanceof BankFinanceState);

            BankFinanceState inB = (BankFinanceState) tx.getInput(0);
            BankFinanceState opB = (BankFinanceState) tx.getOutput(0);

            req.using("Finanace agency signature not present in the transaction", requiredSigners.contains(opB.getFinanceAgencyNode().getOwningKey()));
            req.using("Bank signature not present in the transaction", requiredSigners.contains(opB.getBankNode().getOwningKey()));
            req.using("Input credit description shall not be UNSPECIFIED or POOR",
                    opB.getCreditScoreDesc() != CreditScoreDesc.UNSPECIFIED || opB.getCreditScoreDesc() != CreditScoreDesc.POOR);
            req.using("Initial processing status shall be IN_PROCESSING", inB.getBankProcessingStatus() == BankProcessingStatus.IN_PROCESSING);
            req.using("Output processing status shall not be IN_PROCESSING", opB.getBankProcessingStatus() == BankProcessingStatus.PROCESSED
                    || opB.getBankProcessingStatus() == BankProcessingStatus.REJECTED);
            return null;
        });
    }

    public interface Commands extends CommandData {
        public class BankProcessingInitiated implements BankFinanceValidationContract.Commands {
        }

        public class LoanRequestProcessed implements BankFinanceValidationContract.Commands {
        }
    }
}
