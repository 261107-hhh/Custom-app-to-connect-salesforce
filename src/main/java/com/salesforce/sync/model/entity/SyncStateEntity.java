package com.salesforce.sync.model.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "_sync_state")
public class SyncStateEntity {

    @Id
    @Column(name = "object_name", length = 100)
    private String objectName;

    @Column(name = "last_sync_timestamp", length = 50)
    private String lastSyncTimestamp;

    @Column(name = "last_sync_mode", length = 50)
    private String lastSyncMode;

    @Column(name = "total_records")
    private Long totalRecords = 0L;

    @Column(name = "last_status", length = 50)
    private String lastStatus;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public SyncStateEntity() {}

    public SyncStateEntity(String objectName, String lastSyncTimestamp, String lastSyncMode, Long totalRecords, String lastStatus) {
        this.objectName = objectName;
        this.lastSyncTimestamp = lastSyncTimestamp;
        this.lastSyncMode = lastSyncMode;
        this.totalRecords = totalRecords;
        this.lastStatus = lastStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public String getObjectName() { return objectName; }
    public void setObjectName(String objectName) { this.objectName = objectName; }
    @JsonProperty("object_name")
    public String getObjectNameSnake() { return objectName; }

    public String getLastSyncTimestamp() { return lastSyncTimestamp; }
    public void setLastSyncTimestamp(String lastSyncTimestamp) { this.lastSyncTimestamp = lastSyncTimestamp; }
    @JsonProperty("last_sync_timestamp")
    public String getLastSyncTimestampSnake() { return lastSyncTimestamp; }

    public String getLastSyncMode() { return lastSyncMode; }
    public void setLastSyncMode(String lastSyncMode) { this.lastSyncMode = lastSyncMode; }
    @JsonProperty("last_sync_mode")
    public String getLastSyncModeSnake() { return lastSyncMode; }

    public Long getTotalRecords() { return totalRecords; }
    public void setTotalRecords(Long totalRecords) { this.totalRecords = totalRecords; }
    @JsonProperty("total_records")
    public Long getTotalRecordsSnake() { return totalRecords; }

    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }
    @JsonProperty("last_status")
    public String getLastStatusSnake() { return lastStatus; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    @JsonProperty("updated_at")
    public LocalDateTime getUpdatedAtSnake() { return updatedAt; }
}
