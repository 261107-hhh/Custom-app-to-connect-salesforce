package com.salesforce.sync.model.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "_sync_history")
public class SyncHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_name", nullable = false, length = 100)
    private String objectName;

    @Column(name = "sync_mode", nullable = false, length = 50)
    private String syncMode;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "records_fetched")
    private Integer recordsFetched = 0;

    @Column(name = "records_upserted")
    private Integer recordsUpserted = 0;

    @Column(name = "start_time")
    private LocalDateTime startTime = LocalDateTime.now();

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "user_email", length = 150)
    private String userEmail;

    public SyncHistoryEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getObjectName() { return objectName; }
    public void setObjectName(String objectName) { this.objectName = objectName; }
    @JsonProperty("object_name")
    public String getObjectNameSnake() { return objectName; }

    public String getSyncMode() { return syncMode; }
    public void setSyncMode(String syncMode) { this.syncMode = syncMode; }
    @JsonProperty("sync_mode")
    public String getSyncModeSnake() { return syncMode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getRecordsFetched() { return recordsFetched; }
    public void setRecordsFetched(Integer recordsFetched) { this.recordsFetched = recordsFetched; }
    @JsonProperty("records_fetched")
    public Integer getRecordsFetchedSnake() { return recordsFetched; }

    public Integer getRecordsUpserted() { return recordsUpserted; }
    public void setRecordsUpserted(Integer recordsUpserted) { this.recordsUpserted = recordsUpserted; }
    @JsonProperty("records_upserted")
    public Integer getRecordsUpsertedSnake() { return recordsUpserted; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    @JsonProperty("start_time")
    public LocalDateTime getStartTimeSnake() { return startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    @JsonProperty("end_time")
    public LocalDateTime getEndTimeSnake() { return endTime; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    @JsonProperty("duration_ms")
    public Long getDurationMsSnake() { return durationMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    @JsonProperty("error_message")
    public String getErrorMessageSnake() { return errorMessage; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    @JsonProperty("user_email")
    public String getUserEmailSnake() { return userEmail; }
}
