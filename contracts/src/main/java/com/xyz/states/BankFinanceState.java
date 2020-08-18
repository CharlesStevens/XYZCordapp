package com.xyz.states;

import com.xyz.constants.BankProcessingStatus;
import com.xyz.constants.CreditScoreDesc;
import com.xyz.contracts.BankFinanceValidationContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.serialization.ConstructorForDeserialization;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

@BelongsToContract(BankFinanceValidationContract.class)
public class BankFinanceState implements LinearState {

    private Party financeAgencyNode;
    private Party bankNode;
    private String companyName;
    private String businessType;
    private int loanAmount;
    private CreditScoreDesc creditScoreDesc;
    private BankProcessingStatus processingStatus;
    private UniqueIdentifier bankLoanProcessingId;

    @ConstructorForDeserialization
    public BankFinanceState(Party financeAgencyNode, Party bankNode, String companyName, String businessType, int loanAmount, CreditScoreDesc creditScoreDesc, BankProcessingStatus processingStatus, UniqueIdentifier bankLoanProcessingId) {
        this.financeAgencyNode = financeAgencyNode;
        this.bankNode = bankNode;
        this.companyName = companyName;
        this.businessType = businessType;
        this.loanAmount = loanAmount;
        this.creditScoreDesc = creditScoreDesc;
        this.processingStatus = processingStatus;
        this.bankLoanProcessingId = bankLoanProcessingId;
    }

    public Party getFinanceAgencyNode() {
        return financeAgencyNode;
    }

    public void setFinanceAgencyNode(Party financeAgencyNode) {
        this.financeAgencyNode = financeAgencyNode;
    }

    public Party getBankNode() {
        return bankNode;
    }

    public void setBankNode(Party bankNode) {
        this.bankNode = bankNode;
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

    public CreditScoreDesc getCreditScoreDesc() {
        return creditScoreDesc;
    }

    public void setCreditScoreDesc(CreditScoreDesc creditScoreDesc) {
        this.creditScoreDesc = creditScoreDesc;
    }

    public BankProcessingStatus getBankProcessingStatus() {
        return this.processingStatus;
    }

    public void setBankProcessingStatus(BankProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public UniqueIdentifier getBankLoanProcessingId() {
        return bankLoanProcessingId;
    }

    public void setBankLoanProcessingId(UniqueIdentifier bankLoanProcessingId) {
        this.bankLoanProcessingId = bankLoanProcessingId;
    }

    @NotNull
    @Override
    public UniqueIdentifier getLinearId() {
        return bankLoanProcessingId;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return Arrays.asList(financeAgencyNode, bankNode);
    }
}