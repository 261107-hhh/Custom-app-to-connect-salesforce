package com.salesforce.sync.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "_organizations", indexes = {
    @Index(name = "idx_org_slug", columnList = "slug", unique = true),
    @Index(name = "idx_org_status", columnList = "status")
})
public class OrganizationEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id; // e.g. "default_org", "org_acme_corp" or UUID

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 60)
    private String slug;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "ACTIVE"; // ACTIVE, SUSPENDED

    // --- Per-Organization Salesforce Configuration ---
    @Column(name = "sf_instance_url", length = 255)
    private String sfInstanceUrl;

    @Column(name = "sf_auth_mode", length = 30)
    private String sfAuthMode = "disconnected"; // "disconnected", "eca", "password", "session", "mock"

    @Column(name = "sf_client_id", length = 255)
    private String sfClientId;

    @Column(name = "sf_client_secret_encrypted", length = 500)
    private String sfClientSecretEncrypted;

    @Column(name = "sf_username", length = 150)
    private String sfUsername;

    @Column(name = "sf_password_encrypted", length = 500)
    private String sfPasswordEncrypted;

    @Column(name = "sf_security_token_encrypted", length = 500)
    private String sfSecurityTokenEncrypted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public OrganizationEntity() {}

    public OrganizationEntity(String id, String name, String slug) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.status = "ACTIVE";
        this.sfAuthMode = "disconnected";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSfInstanceUrl() { return sfInstanceUrl; }
    public void setSfInstanceUrl(String sfInstanceUrl) { this.sfInstanceUrl = sfInstanceUrl; }

    public String getSfAuthMode() { return sfAuthMode; }
    public void setSfAuthMode(String sfAuthMode) { this.sfAuthMode = sfAuthMode; }

    public String getSfClientId() { return sfClientId; }
    public void setSfClientId(String sfClientId) { this.sfClientId = sfClientId; }

    public String getSfClientSecretEncrypted() { return sfClientSecretEncrypted; }
    public void setSfClientSecretEncrypted(String sfClientSecretEncrypted) { this.sfClientSecretEncrypted = sfClientSecretEncrypted; }

    public String getSfUsername() { return sfUsername; }
    public void setSfUsername(String sfUsername) { this.sfUsername = sfUsername; }

    public String getSfPasswordEncrypted() { return sfPasswordEncrypted; }
    public void setSfPasswordEncrypted(String sfPasswordEncrypted) { this.sfPasswordEncrypted = sfPasswordEncrypted; }

    public String getSfSecurityTokenEncrypted() { return sfSecurityTokenEncrypted; }
    public void setSfSecurityTokenEncrypted(String sfSecurityTokenEncrypted) { this.sfSecurityTokenEncrypted = sfSecurityTokenEncrypted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean isSalesforceConfigured() {
        if (sfAuthMode == null || "disconnected".equalsIgnoreCase(sfAuthMode)) {
            return false;
        }
        return "mock".equalsIgnoreCase(sfAuthMode) || (sfInstanceUrl != null && !sfInstanceUrl.isBlank());
    }
}
