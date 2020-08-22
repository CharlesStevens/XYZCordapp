package com.xyz.webserver.data;

import javax.xml.bind.annotation.XmlRootElement;

public class LoanApplicationData {

	private Long loanAmount;
	private String borrowerCompany;
	private String borrowerBusinessType;

	public LoanApplicationData() {
	}

	public LoanApplicationData(Long loanAmount, String borrowerCompany, String borrowerBusinessType) {
		this.loanAmount = loanAmount;
		this.borrowerCompany = borrowerCompany;
		this.borrowerBusinessType = borrowerBusinessType;
	}

	public Long getLoanAmount() {
		return loanAmount;
	}

	public void setLoanAmount(Long loanAmount) {
		this.loanAmount = loanAmount;
	}

	public String getBorrowerCompany() {
		return borrowerCompany;
	}

	public void setBorrowerCompany(String borrowerCompany) {
		this.borrowerCompany = borrowerCompany;
	}

	public String getBorrowerBusinessType() {
		return borrowerBusinessType;
	}

	public void setBorrowerBusinessType(String borrowerBusinessType) {
		this.borrowerBusinessType = borrowerBusinessType;
	}

}
