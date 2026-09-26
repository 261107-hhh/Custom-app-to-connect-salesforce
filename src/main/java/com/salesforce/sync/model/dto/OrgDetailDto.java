package com.salesforce.sync.model.dto;

import java.time.LocalDateTime;

public class OrgDetailDto {
    private String id;
    private String name;
    private String slug;
    private String status;
    private boolean salesforceConnected;
    private String sfAuthMode;
    private String sfInstanceUrl;
    private String sfUsername;
    private String currentUserRole;
    private long memberCount;
    private LocalDateTime createdAt;

    public OrgDetailDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isSalesforceConnected() { return salesforceConnected; }
    public void setSalesforceConnected(boolean salesforceConnected) { this.salesforceConnected = salesforceConnected; }

    public String getSfAuthMode() { return sfAuthMode; }
    public void setSfAuthMode(String sfAuthMode) { this.sfAuthMode = sfAuthMode; }

    public String getSfInstanceUrl() { return sfInstanceUrl; }
    public void setSfInstanceUrl(String sfInstanceUrl) { this.sfInstanceUrl = sfInstanceUrl; }

    public String getSfUsername() { return sfUsername; }
    public void setSfUsername(String sfUsername) { this.sfUsername = sfUsername; }

    public String getCurrentUserRole() { return currentUserRole; }
    public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }

    public long getMemberCount() { return memberCount; }
    public void setMemberCount(long memberCount) { this.memberCount = memberCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
