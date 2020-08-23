package com.xyz.webserver.data;

public class LoanApplicationException {

    private String exceptionMessage;

    public LoanApplicationException() {

    }

    public LoanApplicationException(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

}
