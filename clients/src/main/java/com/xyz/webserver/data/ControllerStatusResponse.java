package com.xyz.webserver.data;

import java.util.Map;

public class ControllerStatusResponse {

    Map<String, String> applicationStatuses;

    public ControllerStatusResponse() {

    }

    public ControllerStatusResponse(Map<String, String> applicationStatuses) {
        this.applicationStatuses = applicationStatuses;
    }

    public Map<String, String> getApplicationStatuses() {
        return applicationStatuses;
    }

    public void setApplicationStatuses(Map<String, String> applicationStatuses) {
        this.applicationStatuses = applicationStatuses;
    }
}
