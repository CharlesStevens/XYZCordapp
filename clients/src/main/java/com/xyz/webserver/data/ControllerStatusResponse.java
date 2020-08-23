package com.xyz.webserver.data;

import java.util.List;
import java.util.Map;

public class ControllerStatusResponse {
    public static final String LOAN_APPLICATION_ID = "Loan Application ID";
    public static final String CREDIT_CHECK_VERIFICATION_ID = "Credit Check Verification ID";
    public static final String BANK_PROCESSING_ID = "Bank Processing ID";
    public static final String STATUS = "Status";
    public static final String CREDIT_SCORE = "Credit Score";
    public static final String CREDIT_SCORE_DESC = "Credit Score Description";

    private List<Map<String, String>> applicationStatuses;

    public ControllerStatusResponse() {

    }

    public ControllerStatusResponse(List<Map<String, String>> applicationStatuses) {
        this.applicationStatuses = applicationStatuses;
    }

    public List<Map<String, String>> getApplicationStatuses() {
        return applicationStatuses;
    }

    public void setApplicationStatuses(List<Map<String, String>> applicationStatuses) {
        this.applicationStatuses = applicationStatuses;
    }
}
