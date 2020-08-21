package com.xyz.contracts;

import static net.corda.core.contracts.ContractsDSL.requireThat;

import java.security.PublicKey;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xyz.constants.LoanApplicationStatus;
import com.xyz.states.LoanApplicationState;

import net.corda.core.contracts.Command;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;

public class LoanApplicationContract implements Contract {
	private static final Logger LOG = LoggerFactory.getLogger(LoanApplicationContract.class.getName());

	@Override
	public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {
		if (tx != null && tx.getCommands().size() != 1)
			throw new IllegalArgumentException("More than one command for Single Transaction : INVALID");

		Command command = tx.getCommand(0);
		List<PublicKey> requiredSigners = command.getSigners();
		CommandData commandType = command.getValue();

		if (commandType instanceof Commands.LoanApplied) {
			verifyLoanApplication(tx, requiredSigners);
		} else if (commandType instanceof Commands.LoanForwardedToCreditCheck) {
			verifyLoanStatusChangeOnCAForward(tx, requiredSigners);
		} else if (commandType instanceof Commands.LoanProcesedFromCreditCheck) {
			verifyLoanStatusOnProcessingFromCA(tx, requiredSigners);
		} else if (commandType instanceof Commands.LoanApplicationForwaredToBank) {
			verifyLoanStatusChangeOnBankForward(tx, requiredSigners);
		} else if (commandType instanceof Commands.LoanProcessedFromBank) {
			verifyLoanStatusChangeOnProcessingFromBank(tx, requiredSigners);
		}

	}

	private void verifyLoanApplication(LedgerTransaction tx, List<PublicKey> requiredSigners) {
		requireThat(req -> {
			req.using("Only one transaction signer expected", requiredSigners.size() == 1);
			req.using("Only one output should be created during the process LoanApplication",
					tx.getOutputStates().size() == 1);

			req.using("Output state shall be of type LoanApplicationState",
					tx.getOutput(0) instanceof LoanApplicationState);
			req.using("No input should be consumed while initiating loan", tx.getInputStates().isEmpty());

			LoanApplicationState applicationState = (LoanApplicationState) tx.getOutput(0);

			req.using("Finanace agency signature not present in the transaction",
					requiredSigners.contains(applicationState.getFinanceAgencyNode().getOwningKey()));
			req.using("Initial state shall be {APPLIED} ",
					applicationState.getApplicationStatus() == LoanApplicationStatus.APPLIED);
			req.using("Minimum Loan amount Validation failed", applicationState.getLoanAmount() > 0);
			req.using("Borrowing Company name cant be Empty",
					applicationState.getCompanyName() != null || !applicationState.getCompanyName().equals(""));
			req.using("Borrowing Company`s Business Type cant be Empty",
					applicationState.getBusinessType() != null || !applicationState.getBusinessType().equals(""));
			return null;
		});
	}

	private void verifyLoanStatusChangeOnCAForward(LedgerTransaction tx, List<PublicKey> requiredSigners) {
		requireThat(req -> {
			req.using("Only one transaction signer expected", requiredSigners.size() == 1);
			req.using("Only one output should be created during the process LoanApplication",
					tx.getOutputStates().size() == 1);

			req.using("Output state shall be of type LoanApplicationState",
					tx.getOutput(0) instanceof LoanApplicationState);
			req.using("Input state shall be of type LoanApplicationState",
					tx.getInput(0) instanceof LoanApplicationState);

			LoanApplicationState inputState = (LoanApplicationState) tx.getInput(0);
			LoanApplicationState outputState = (LoanApplicationState) tx.getOutput(0);

			req.using("Input Loan application status shall be APPLIED",
					inputState.getApplicationStatus() == LoanApplicationStatus.APPLIED);
			req.using("Output Loan application status shall be FORWARDED_TO_CREDIT_CHECK_AGENCY",
					outputState.getApplicationStatus() == LoanApplicationStatus.FORWARDED_TO_CREDIT_CHECK_AGENCY);

			return null;
		});
	}

	private void verifyLoanStatusOnProcessingFromCA(LedgerTransaction tx, List<PublicKey> requiredSigners) {
		requireThat(req -> {
			req.using("Only one transaction signer expected", requiredSigners.size() == 1);
			req.using("Only one output should be created during the process LoanApplication",
					tx.getOutputStates().size() == 1);

			req.using("Output state shall be of type LoanApplicationState",
					tx.getOutput(0) instanceof LoanApplicationState);
			req.using("Input state shall be of type LoanApplicationState",
					tx.getInput(0) instanceof LoanApplicationState);

			LoanApplicationState inputState = (LoanApplicationState) tx.getInput(0);
			LoanApplicationState outputState = (LoanApplicationState) tx.getOutput(0);

			req.using("Input Loan application status shall be FORWARDED_TO_CREDIT_CHECK_AGENCY",
					inputState.getApplicationStatus() == LoanApplicationStatus.FORWARDED_TO_CREDIT_CHECK_AGENCY);
			req.using("Output Loan application status shall be CREDIT_SCORE_CHECK_FAILED OR CREDIT_SCORE_CHECK_PASSED",
					outputState.getApplicationStatus() == LoanApplicationStatus.CREDIT_SCORE_CHECK_PASS
							|| outputState.getApplicationStatus() == LoanApplicationStatus.CREDIT_SCORE_CHECK_FAILED);
			return null;
		});
	}

	private void verifyLoanStatusChangeOnBankForward(LedgerTransaction tx, List<PublicKey> requiredSigners) {
		requireThat(req -> {
			req.using("Only one transaction signer expected", requiredSigners.size() == 1);
			req.using("Only one output should be created during the process LoanApplication",
					tx.getOutputStates().size() == 1);

			req.using("Output state shall be of type LoanApplicationState",
					tx.getOutput(0) instanceof LoanApplicationState);
			req.using("Input state shall be of type LoanApplicationState",
					tx.getInput(0) instanceof LoanApplicationState);

			LoanApplicationState inputState = (LoanApplicationState) tx.getInput(0);
			LoanApplicationState outputState = (LoanApplicationState) tx.getOutput(0);

			req.using("Input Loan application status shall be CREDIT_SCORE_CHECK_PASS",
					inputState.getApplicationStatus() == LoanApplicationStatus.CREDIT_SCORE_CHECK_PASS);
			req.using("Output Loan application status shall be FORWARDED_TO_BANK",
					outputState.getApplicationStatus() == LoanApplicationStatus.FORWARDED_TO_BANK);
			return null;
		});
	}

	private void verifyLoanStatusChangeOnProcessingFromBank(LedgerTransaction tx, List<PublicKey> requiredSigners) {
		requireThat(req -> {
			req.using("Only one transaction signer expected", requiredSigners.size() == 1);
			req.using("Only one output should be created during the process LoanApplication",
					tx.getOutputStates().size() == 1);

			req.using("Output state shall be of type LoanApplicationState",
					tx.getOutput(0) instanceof LoanApplicationState);
			req.using("Input state shall be of type LoanApplicationState",
					tx.getInput(0) instanceof LoanApplicationState);

			LoanApplicationState inputState = (LoanApplicationState) tx.getInput(0);
			LoanApplicationState outputState = (LoanApplicationState) tx.getOutput(0);

			req.using("Input Loan application status shall be FORWARDED_TO_BANK",
					inputState.getApplicationStatus() == LoanApplicationStatus.FORWARDED_TO_BANK);
			req.using("Output Loan application status shall be REJECTED_FROM_BANK OR LOAN_DISBURSED",
					outputState.getApplicationStatus() == LoanApplicationStatus.LOAN_DISBURSED
							|| outputState.getApplicationStatus() == LoanApplicationStatus.REJECTED_FROM_BANK);
			return null;
		});
	}

	public interface Commands extends CommandData {
		public class LoanApplied implements Commands {
		}

		public class LoanForwardedToCreditCheck implements Commands {
		}

		public class LoanProcesedFromCreditCheck implements Commands {
		}

		public class LoanApplicationForwaredToBank implements Commands {
		}

		public class LoanProcessedFromBank implements Commands {
		}
	}
}