package com.xyz.states;

import com.xyz.constants.CreditScoreDesc;
import com.xyz.contracts.CreditRatingCheckContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.serialization.ConstructorForDeserialization;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

@BelongsToContract(CreditRatingCheckContract.class)
public class CreditRatingState implements LinearState {

    private Party loaningAgency;
    private Party creditAgencyNode;
    private String companyName;
    private String businessType;
    private int loanAmount;
    private Double creditScoreCheckRating;
    private CreditScoreDesc creditScoreDesc;
    private UniqueIdentifier loanVerificationId;

    @ConstructorForDeserialization
    public CreditRatingState(Party loaningAgency, Party creditAgencyNode, String companyName, String businessType, int loanAmount, Double creditScoreCheckRating, CreditScoreDesc creditScoreDesc, UniqueIdentifier loanVerificationId) {
        this.loaningAgency = loaningAgency;
        this.creditAgencyNode = creditAgencyNode;
        this.companyName = companyName;
        this.businessType = businessType;
        this.loanAmount = loanAmount;
        this.creditScoreCheckRating = creditScoreCheckRating;
        this.creditScoreDesc = creditScoreDesc;
        this.loanVerificationId = loanVerificationId;
    }

    public Party getLoaningAgency() {
        return loaningAgency;
    }

    public void setLoaningAgency(Party loaningAgency) {
        this.loaningAgency = loaningAgency;
    }

    public Party getCreditAgencyNode() {
        return creditAgencyNode;
    }

    public void setCreditAgencyNode(Party creditAgencyNode) {
        this.creditAgencyNode = creditAgencyNode;
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

    public Double getCreditScoreCheckRating() {
        return creditScoreCheckRating;
    }

    public void setCreditScoreCheckRating(Double creditScoreCheckRating) {
        this.creditScoreCheckRating = creditScoreCheckRating;
    }

    public CreditScoreDesc getCreditScoreDesc() {
        return creditScoreDesc;
    }

    public void setApplicationStatus(CreditScoreDesc creditScoreDesc) {
        this.creditScoreDesc = creditScoreDesc;
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
        return loanVerificationId;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return Arrays.asList(loaningAgency, creditAgencyNode);
    }
}

