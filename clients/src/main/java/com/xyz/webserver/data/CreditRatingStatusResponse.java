package com.xyz.webserver.data;

import java.util.Map;

public class CreditRatingStatusResponse {
    Map<String, CreditCheckRatings> creditResponse;

    public CreditRatingStatusResponse() {
    }

    public CreditRatingStatusResponse(Map<String, CreditCheckRatings> creditResponse) {
        this.creditResponse = creditResponse;
    }

    public Map<String, CreditCheckRatings> getCreditResponse() {
        return creditResponse;
    }

    public void setCreditResponse(Map<String, CreditCheckRatings> creditResponse) {
        this.creditResponse = creditResponse;
    }
}

