package com.salesforce.sync.model.dto;

public class RegisterOrgRequest {
    private String organizationName;
    private String slug;
    private String adminName;
    private String adminEmail;
    private String adminPassword;

    public RegisterOrgRequest() {}

    public RegisterOrgRequest(String organizationName, String slug, String adminName, String adminEmail, String adminPassword) {
        this.organizationName = organizationName;
        this.slug = slug;
        this.adminName = adminName;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getAdminName() { return adminName; }
    public void setAdminName(String adminName) { this.adminName = adminName; }

    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }

    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
}
