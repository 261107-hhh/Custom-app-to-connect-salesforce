package com.salesforce.sync.model.dto;

public class SwitchOrgRequest {
    private String organizationId;

    public SwitchOrgRequest() {}

    public SwitchOrgRequest(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
}
