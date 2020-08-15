//package com.xyz.states;
//
//import com.xyz.constants.LoanDisbursalStatus;
//
////import com.xyz.contracts.CreditRatingCheckContract;
////import net.corda.core.contracts.BelongsToContract;
//import net.corda.core.contracts.LinearState;
//import net.corda.core.contracts.UniqueIdentifier;
//import net.corda.core.identity.AbstractParty;
//import net.corda.core.identity.Party;
//import org.jetbrains.annotations.NotNull;
//
//import java.util.Arrays;
//import java.util.List;
//
////@BelongsToContract(CreditRatingCheckContract.class)
//public class BankFinanceState implements LinearState {
//
//    private Party financeAgencyNode;
//    private Party bankNode;
//    private String companyName;
//    private String businessType;
//    private int loanAmount;
//    private LoanDisbursalStatus loanDisbursalStatus;
//    private UniqueIdentifier loanApplicationId;
//
//    public BankFinanceState(Party financeAgencyNode, Party bankNode, String companyName, String businessType, int loanAmount, LoanDisbursalStatus loanDisbursalStatus, UniqueIdentifier loanApplicationId) {
//        this.financeAgencyNode = financeAgencyNode;
//        this.bankNode = bankNode;
//        this.companyName = companyName;
//        this.businessType = businessType;
//        this.loanAmount = loanAmount;
//        this.loanDisbursalStatus = loanDisbursalStatus;
//        this.loanApplicationId = loanApplicationId;
//    }
//
//    public Party getFinanceAgencyNode() {
//        return financeAgencyNode;
//    }
//
//    public void setFinanceAgencyNode(Party financeAgencyNode) {
//        this.financeAgencyNode = financeAgencyNode;
//    }
//
//    public Party getBankNode() {
//        return bankNode;
//    }
//
//    public void setBankNode(Party bankNode) {
//        this.bankNode = bankNode;
//    }
//
//    public String getCompanyName() {
//        return companyName;
//    }
//
//    public void setCompanyName(String companyName) {
//        this.companyName = companyName;
//    }
//
//    public String getBusinessType() {
//        return businessType;
//    }
//
//    public void setBusinessType(String businessType) {
//        this.businessType = businessType;
//    }
//
//    public int getLoanAmount() {
//        return loanAmount;
//    }
//
//    public void setLoanAmount(int loanAmount) {
//        this.loanAmount = loanAmount;
//    }
//
//    public LoanDisbursalStatus getLoanDisbursalStatus() {
//        return loanDisbursalStatus;
//    }
//
//    public void setLoanDisbursalStatus(LoanDisbursalStatus loanDisbursalStatus) {
//        this.loanDisbursalStatus = loanDisbursalStatus;
//    }
//
//    public UniqueIdentifier getLoanApplicationId() {
//        return loanApplicationId;
//    }
//
//    public void setLoanApplicationId(UniqueIdentifier loanApplicationId) {
//        this.loanApplicationId = loanApplicationId;
//    }
//
//    @NotNull
//    @Override
//    public UniqueIdentifier getLinearId() {
//        return loanApplicationId;
//    }
//
//    @NotNull
//    @Override
//    public List<AbstractParty> getParticipants() {
//        return Arrays.asList(financeAgencyNode, bankNode);
//    }
//}
