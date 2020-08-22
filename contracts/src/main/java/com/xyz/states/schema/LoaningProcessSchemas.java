package com.xyz.states.schema;

import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Arrays;
import java.util.UUID;

public class LoaningProcessSchemas extends MappedSchema {
    public LoaningProcessSchemas() {
        super(ApplicationStateSchema.class, 1, Arrays.asList(PersistentLoanApplicationState.class,
                PersistentCreditRatingSchema.class, PersistentBankProcessingSchema.class));
    }

    @Entity
    @Table(name = "loan_application_schema")
    public static class PersistentLoanApplicationState extends PersistentState {

        @Column(name = "financeAgency")
        private String financeAgency;

        @Column(name = "companyName")
        private String companyName;

        @Column(name = "businessType")
        private String businessType;

        @Column(name = "loanAmount")
        private Long loanAmount;

        @Column(name = "applicationStatus")
        private String applicationStatus;

        @Column(name = "loanApplicationId")
        private UUID loanApplicationId;

        @Column(name = "loanVerificationId")
        private UUID loanVerificationId;

        @Column(name = "bankProcessingId")
        private UUID bankProcessingId;

        public PersistentLoanApplicationState() {
        }

        public PersistentLoanApplicationState(String financeAgency, String companyName, String businessType,
                                              Long loanAmount, String applicationStatus, UUID loanApplicationId, UUID loanVerificationId,
                                              UUID bankProcessingId) {
            this.financeAgency = financeAgency;
            this.companyName = companyName;
            this.businessType = businessType;
            this.loanAmount = loanAmount;
            this.applicationStatus = applicationStatus;
            this.loanApplicationId = loanApplicationId;
            this.loanVerificationId = loanVerificationId;
            this.bankProcessingId = bankProcessingId;
        }

        public String getFinanceAgency() {
            return financeAgency;
        }

        public void setFinanceAgency(String financeAgency) {
            this.financeAgency = financeAgency;
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

        public Long getLoanAmount() {
            return loanAmount;
        }

        public void setLoanAmount(Long loanAmount) {
            this.loanAmount = loanAmount;
        }

        public String getApplicationStatus() {
            return applicationStatus;
        }

        public void setApplicationStatus(String applicationStatus) {
            this.applicationStatus = applicationStatus;
        }

        public UUID getLoanApplicationId() {
            return loanApplicationId;
        }

        public void setLoanApplicationId(UUID loanApplicationId) {
            this.loanApplicationId = loanApplicationId;
        }

        public UUID getLoanVerificationId() {
            return loanVerificationId;
        }

        public void setLoanVerificationId(UUID loanVerificationId) {
            this.loanVerificationId = loanVerificationId;
        }

        public UUID getBankProcessingId() {
            return bankProcessingId;
        }

        public void setBankProcessingId(UUID bankProcessingId) {
            this.bankProcessingId = bankProcessingId;
        }

    }

    @Entity
    @Table(name = "credit_rating_state")
    public static class PersistentCreditRatingSchema extends PersistentState {

        @Column(name = "loaningAgency")
        private String loaningAgency;

        @Column(name = "creditAgency")
        private String creditAgency;

        @Column(name = "companyName")
        private String companyName;

        @Column(name = "businessType")
        private String businessType;

        @Column(name = "loanAmount")
        private Long loanAmount;

        @Column(name = "creditScoreCheckRating")
        private Double creditScoreCheckRating;

        @Column(name = "creditScoreDesc")
        private String creditScoreDesc;

        @Column(name = "loanVerificationId")
        private UUID loanVerificationId;

        public PersistentCreditRatingSchema() {
        }

        public PersistentCreditRatingSchema(String loaningAgency, String creditAgency, String companyName, String businessType,
                                            Long loanAmount, Double creditScoreCheckRating, String creditScoreDesc,
                                            UUID loanVerificationId) {
            this.loaningAgency = loaningAgency;
            this.creditAgency = creditAgency;
            this.companyName = companyName;
            this.businessType = businessType;
            this.loanAmount = loanAmount;
            this.creditScoreCheckRating = creditScoreCheckRating;
            this.creditScoreDesc = creditScoreDesc;
            this.loanVerificationId = loanVerificationId;
        }

        public String getLoaningAgency() {
            return loaningAgency;
        }

        public void setLoaningAgency(String loaningAgency) {
            this.loaningAgency = loaningAgency;
        }

        public String getCreditAgency() {
            return creditAgency;
        }

        public void setCreditAgency(String creditAgency) {
            this.creditAgency = creditAgency;
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

        public Long getLoanAmount() {
            return loanAmount;
        }

        public void setLoanAmount(Long loanAmount) {
            this.loanAmount = loanAmount;
        }

        public Double getCreditScoreCheckRating() {
            return creditScoreCheckRating;
        }

        public void setCreditScoreCheckRating(Double creditScoreCheckRating) {
            this.creditScoreCheckRating = creditScoreCheckRating;
        }

        public String getCreditScoreDesc() {
            return creditScoreDesc;
        }

        public void setCreditScoreDesc(String creditScoreDesc) {
            this.creditScoreDesc = creditScoreDesc;
        }

        public UUID getLoanVerificationId() {
            return loanVerificationId;
        }

        public void setLoanVerificationId(UUID loanVerificationId) {
            this.loanVerificationId = loanVerificationId;
        }
    }

    @Entity
    @Table(name = "bank_processing_state")
    public static class PersistentBankProcessingSchema extends PersistentState {
        @Column(name = "finanaceAgency")
        private String financeAgency;

        @Column(name = "bank")
        private String bank;

        @Column(name = "companyName")
        private String companyName;

        @Column(name = "businessType")
        private String businessType;

        @Column(name = "loanAmount")
        private Long loanAmount;

        @Column(name = "bankProcessingStatus")
        private String bankProcessingStatus;

        @Column(name = "creditScoreDesc")
        private String creditScoreDesc;

        @Column(name = "bankProcessingId")
        private UUID bankProcessingId;


        public PersistentBankProcessingSchema() {
        }

        public PersistentBankProcessingSchema(String financeAgency, String bank, String companyName, String businessType,
                                              Long loanAmount, String bankProcessingStatus, String creditScoreDesc,
                                              UUID bankProcessingId) {
            this.financeAgency = financeAgency;
            this.bank = bank;
            this.companyName = companyName;
            this.businessType = businessType;
            this.loanAmount = loanAmount;
            this.bankProcessingStatus = bankProcessingStatus;
            this.creditScoreDesc = creditScoreDesc;
            this.bankProcessingId = bankProcessingId;
        }

        public String getFinanceAgency() {
            return financeAgency;
        }

        public void setFinanceAgency(String financeAgency) {
            this.financeAgency = financeAgency;
        }

        public String getBank() {
            return bank;
        }

        public void setBank(String bank) {
            this.bank = bank;
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

        public Long getLoanAmount() {
            return loanAmount;
        }

        public void setLoanAmount(Long loanAmount) {
            this.loanAmount = loanAmount;
        }

        public String getBankProcessingStatus() {
            return bankProcessingStatus;
        }

        public void setBankProcessingStatus(String bankProcessingStatus) {
            this.bankProcessingStatus = bankProcessingStatus;
        }

        public String getCreditScoreDesc() {
            return creditScoreDesc;
        }

        public void setCreditScoreDesc(String creditScoreDesc) {
            this.creditScoreDesc = creditScoreDesc;
        }

        public UUID getBankProcessingId() {
            return bankProcessingId;
        }

        public void setBankProcessingId(UUID bankProcessingId) {
            this.bankProcessingId = bankProcessingId;
        }
    }
}
