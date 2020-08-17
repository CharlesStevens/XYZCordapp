package com.xyz.states;

import com.xyz.constants.LoanApplicationStatus;
import com.xyz.contracts.LoanApplicationContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.serialization.ConstructorForDeserialization;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

@BelongsToContract(LoanApplicationContract.class)
public class LoanApplicationState implements LinearState {

    private Party financeAgencyNode;
    private String companyName;
    private String businessType;
    private int loanAmount;
    private LoanApplicationStatus applicationStatus;
    private UniqueIdentifier loanApplicationId;
    private UniqueIdentifier loanVerificationId;

    @ConstructorForDeserialization
    public LoanApplicationState(Party financeAgencyNode, String companyName, String businessType, int loanAmount, LoanApplicationStatus applicationStatus, UniqueIdentifier loanApplicationId, UniqueIdentifier loanVerificationId) {
        this.financeAgencyNode = financeAgencyNode;
        this.companyName = companyName;
        this.businessType = businessType;
        this.loanAmount = loanAmount;
        this.applicationStatus = applicationStatus;
        this.loanApplicationId = loanApplicationId;
        this.loanVerificationId = loanVerificationId;
    }

    public Party getFinanceAgencyNode() {
        return financeAgencyNode;
    }

    public void setFinanceAgencyNode(Party financeAgencyNode) {
        this.financeAgencyNode = financeAgencyNode;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public int getLoanAmount() {
        return loanAmount;
    }

    public void setLoanAmount(int loanAmount) {
        this.loanAmount = loanAmount;
    }

    public LoanApplicationStatus getApplicationStatus() {
        return applicationStatus;
    }

    public void setApplicationStatus(LoanApplicationStatus applicationStatus) {
        this.applicationStatus = applicationStatus;
    }

    public UniqueIdentifier getLoanApplicationId() {
        return loanApplicationId;
    }

    public void setLoanApplicationId(UniqueIdentifier loanApplicationId) {
        this.loanApplicationId = loanApplicationId;
    }

    public UniqueIdentifier getLoanVerificationId() {
        return loanVerificationId;
    }

    public void setLoanVerificationId(UniqueIdentifier loanVerificationId) {
        this.loanVerificationId = loanVerificationId;
    }

    @NotNull
    @Override
    public UniqueIdentifier getLinearId() {
        return loanApplicationId;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return Arrays.asList(financeAgencyNode);
    }
}