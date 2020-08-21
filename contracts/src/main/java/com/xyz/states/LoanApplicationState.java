package com.xyz.states;

import com.xyz.constants.LoanApplicationStatus;
import com.xyz.contracts.LoanApplicationContract;
import com.xyz.states.schema.LoaningProcessSchemas;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;
import net.corda.core.schemas.QueryableState;
import net.corda.core.serialization.ConstructorForDeserialization;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

@BelongsToContract(LoanApplicationContract.class)
public class LoanApplicationState implements LinearState, QueryableState {

    private Party financeAgencyNode;
    private String companyName;
    private String businessType;
    private Long loanAmount;
    private LoanApplicationStatus applicationStatus;
    private UniqueIdentifier loanApplicationId;
    private UniqueIdentifier loanVerificationId;
    private UniqueIdentifier bankProcessingId;

    @ConstructorForDeserialization
    public LoanApplicationState(Party financeAgencyNode, String companyName, String businessType, Long loanAmount,
                                LoanApplicationStatus applicationStatus, UniqueIdentifier loanApplicationId,
                                UniqueIdentifier loanVerificationId, UniqueIdentifier bankProcessingId) {
        this.financeAgencyNode = financeAgencyNode;
        this.companyName = companyName;
        this.businessType = businessType;
        this.loanAmount = loanAmount;
        this.applicationStatus = applicationStatus;
        this.loanApplicationId = loanApplicationId;
        this.loanVerificationId = loanVerificationId;
        this.bankProcessingId = bankProcessingId;
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

    public Long getLoanAmount() {
        return loanAmount;
    }

    public void setLoanAmount(Long loanAmount) {
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

    public UniqueIdentifier getBankProcessingId() {
        return bankProcessingId;
    }

    public void setBankProcessingId(UniqueIdentifier bankProcessingId) {
        this.bankProcessingId = bankProcessingId;
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

    @Override
    public PersistentState generateMappedObject(MappedSchema schema) {
        if (schema instanceof LoaningProcessSchemas) {
            return new LoaningProcessSchemas.PersistentLoanApplicationState(
                    getFinanceAgencyNode().getName().toString(), getCompanyName(), getBusinessType(),
                    getLoanAmount() == null ? 0 : getLoanAmount().longValue(), getApplicationStatus().toString(), getLoanApplicationId() == null ? null : getLoanApplicationId().getId(),
                    getLoanVerificationId() == null ? null : getLoanVerificationId().getId(), getBankProcessingId() == null ? null : getBankProcessingId().getId());
        } else {
            throw new IllegalArgumentException("Unrecognised schema $schema");
        }
    }

    @Override
    public Iterable<MappedSchema> supportedSchemas() {
        return Arrays.asList(new LoaningProcessSchemas());
    }

}