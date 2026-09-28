package com.salesforce.sync.model.dto;

import java.util.List;

public class AuthResponse {
    private boolean success;
    private String token;
    private String email;
    private String name;
    private String message;
    private String activeOrgId;
    private String activeOrgName;
    private String activeOrgRole;
    private List<OrgSummaryDto> organizations;
    private String defaultOrgId;

    public AuthResponse() {}

    public AuthResponse(boolean success, String token, String email, String name, String message) {
        this.success = success;
        this.token = token;
        this.email = email;
        this.name = name;
        this.message = message;
    }

    public static AuthResponse success(String token, String email, String name, String message) {
        return new AuthResponse(true, token, email, name, message);
    }

    public static AuthResponse error(String message) {
        return new AuthResponse(false, null, null, null, message);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getActiveOrgId() { return activeOrgId; }
    public void setActiveOrgId(String activeOrgId) { this.activeOrgId = activeOrgId; }

    public String getActiveOrgName() { return activeOrgName; }
    public void setActiveOrgName(String activeOrgName) { this.activeOrgName = activeOrgName; }

    public String getActiveOrgRole() { return activeOrgRole; }
    public void setActiveOrgRole(String activeOrgRole) { this.activeOrgRole = activeOrgRole; }

    public List<OrgSummaryDto> getOrganizations() { return organizations; }
    public void setOrganizations(List<OrgSummaryDto> organizations) { this.organizations = organizations; }

    public String getDefaultOrgId() { return defaultOrgId; }
    public void setDefaultOrgId(String defaultOrgId) { this.defaultOrgId = defaultOrgId; }
}
