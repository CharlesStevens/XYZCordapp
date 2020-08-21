package com.xyz.webserver.data;

public class CreditCheckRatings {
    private String creditScore;
    private String creditScoreDescription;

    public CreditCheckRatings(String creditScore, String creditScoreDescription) {
        this.creditScore = creditScore;
        this.creditScoreDescription = creditScoreDescription;
    }

    public String getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(String creditScore) {
        this.creditScore = creditScore;
    }

    public String getCreditScoreDescription() {
        return creditScoreDescription;
    }

    public void setCreditScoreDescription(String creditScoreDescription) {
        this.creditScoreDescription = creditScoreDescription;
    }
}
