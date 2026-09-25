package com.salesforce.sync.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;

@MappedSuperclass
public abstract class BaseAuditableEntity {

    @Column(name = "custom_app_created_by", length = 150)
    private String customAppCreatedBy;

    @Column(name = "custom_app_created_at")
    private LocalDateTime customAppCreatedAt;

    @Column(name = "custom_app_modified_by", length = 150)
    private String customAppModifiedBy;

    @Column(name = "custom_app_modified_at")
    private LocalDateTime customAppModifiedAt;

    @Column(name = "is_custom_app_created", nullable = false)
    private Boolean isCustomAppCreated = false;

    @Column(name = "synced_by", length = 1000)
    private String syncedBy;

    public String getCustomAppCreatedBy() { return customAppCreatedBy; }
    public void setCustomAppCreatedBy(String customAppCreatedBy) { this.customAppCreatedBy = customAppCreatedBy; }

    public LocalDateTime getCustomAppCreatedAt() { return customAppCreatedAt; }
    public void setCustomAppCreatedAt(LocalDateTime customAppCreatedAt) { this.customAppCreatedAt = customAppCreatedAt; }

    public String getCustomAppModifiedBy() { return customAppModifiedBy; }
    public void setCustomAppModifiedBy(String customAppModifiedBy) { this.customAppModifiedBy = customAppModifiedBy; }

    public LocalDateTime getCustomAppModifiedAt() { return customAppModifiedAt; }
    public void setCustomAppModifiedAt(LocalDateTime customAppModifiedAt) { this.customAppModifiedAt = customAppModifiedAt; }

    public Boolean getIsCustomAppCreated() { return isCustomAppCreated; }
    public void setIsCustomAppCreated(Boolean isCustomAppCreated) { this.isCustomAppCreated = isCustomAppCreated; }

    public String getSyncedBy() { return syncedBy; }
    public void setSyncedBy(String syncedBy) { this.syncedBy = syncedBy; }

    /**
     * Records that a specific user has synchronized this record from Salesforce.
     * Supports multiple users syncing the same record while keeping records comma-delimited.
     */
    public void addSyncedBy(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) return;
        String clean = userEmail.trim().toLowerCase();
        if (this.syncedBy == null || this.syncedBy.isBlank()) {
            this.syncedBy = "," + clean + ",";
        } else {
            String current = this.syncedBy.startsWith(",") ? this.syncedBy : "," + this.syncedBy;
            if (!current.endsWith(",")) {
                current = current + ",";
            }
            if (!current.toLowerCase().contains("," + clean + ",")) {
                this.syncedBy = current + clean + ",";
            } else {
                this.syncedBy = current;
            }
        }
    }

    /**
     * Checks if this record is associated with the given user:
     * Either created by the user in the Custom App OR synced by the user via Salesforce.
     */
    public boolean isAssociatedWithUser(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) return false;
        String clean = userEmail.trim().toLowerCase();
        if (this.customAppCreatedBy != null && this.customAppCreatedBy.trim().equalsIgnoreCase(clean)) {
            return true;
        }
        if (this.syncedBy != null && this.syncedBy.toLowerCase().contains("," + clean + ",")) {
            return true;
        }
        return false;
    }

    /**
     * Mark this record as created via the Custom App by a specific user.
     */
    public void markCreatedByCustomApp(String userEmail) {
        this.customAppCreatedBy = userEmail;
        this.customAppCreatedAt = LocalDateTime.now();
        this.customAppModifiedBy = userEmail;
        this.customAppModifiedAt = LocalDateTime.now();
        this.isCustomAppCreated = true;
        addSyncedBy(userEmail);
    }

    /**
     * Mark this record as modified via the Custom App by a specific user.
     */
    public void markModifiedByCustomApp(String userEmail) {
        this.customAppModifiedBy = userEmail;
        this.customAppModifiedAt = LocalDateTime.now();
    }
}
