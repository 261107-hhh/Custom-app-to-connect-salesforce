package com.salesforce.sync.model.dto;

public class OrgSummaryDto {
    private String id;
    private String name;
    private String slug;
    private String role;
    private String status;
    private boolean salesforceConnected;
    private String sfAuthMode;
    private String sfInstanceUrl;
    private boolean isDefault;

    public OrgSummaryDto() {}

    public OrgSummaryDto(String id, String name, String slug, String role, String status, boolean salesforceConnected, String sfAuthMode, String sfInstanceUrl) {
        this(id, name, slug, role, status, salesforceConnected, sfAuthMode, sfInstanceUrl, false);
    }

    public OrgSummaryDto(String id, String name, String slug, String role, String status, boolean salesforceConnected, String sfAuthMode, String sfInstanceUrl, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.role = role;
        this.status = status;
        this.salesforceConnected = salesforceConnected;
        this.sfAuthMode = sfAuthMode;
        this.sfInstanceUrl = sfInstanceUrl;
        this.isDefault = isDefault;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isSalesforceConnected() { return salesforceConnected; }
    public void setSalesforceConnected(boolean salesforceConnected) { this.salesforceConnected = salesforceConnected; }

    public String getSfAuthMode() { return sfAuthMode; }
    public void setSfAuthMode(String sfAuthMode) { this.sfAuthMode = sfAuthMode; }

    public String getSfInstanceUrl() { return sfInstanceUrl; }
    public void setSfInstanceUrl(String sfInstanceUrl) { this.sfInstanceUrl = sfInstanceUrl; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }
}
