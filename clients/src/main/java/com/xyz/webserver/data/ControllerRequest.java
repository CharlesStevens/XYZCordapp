package com.xyz.webserver.data;

public class ControllerRequest {
    String applicationID;

    public ControllerRequest(String applicationID) {
        this.applicationID = applicationID;
    }

    public String getApplicationID() {
        return applicationID;
    }

    public void setApplicationID(String applicationID) {
        this.applicationID = applicationID;
    }


}
