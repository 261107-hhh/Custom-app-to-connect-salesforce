package com.salesforce.sync.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

@Entity
@Table(name = "sf_opportunity", indexes = {
    @Index(name = "idx_opp_name", columnList = "name"),
    @Index(name = "idx_opp_account", columnList = "account_id")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class OpportunityEntity extends BaseAuditableEntity {

    @Id
    @Column(length = 30)
    private String id;

    @Column(length = 255)
    private String name;

    @Column(name = "stage_name", length = 100)
    private String stageName;

    @Column
    private Double amount;

    @Column(name = "close_date", length = 50)
    private String closeDate;

    @Column
    private Double probability;

    @Column(length = 100)
    private String type;

    @Column(name = "created_date", length = 50)
    private String createdDate;

    @Column(name = "last_modified_date", length = 50)
    private String lastModifiedDate;

    @Column(name = "system_modstamp", length = 50)
    private String systemModstamp;

    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    @Column(name = "synced_at", length = 50)
    private String syncedAt;

    @JsonIgnoreProperties({"contacts", "opportunities", "hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private AccountEntity account;

    public OpportunityEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getCloseDate() { return closeDate; }
    public void setCloseDate(String closeDate) { this.closeDate = closeDate; }

    public Double getProbability() { return probability; }
    public void setProbability(Double probability) { this.probability = probability; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }

    public String getLastModifiedDate() { return lastModifiedDate; }
    public void setLastModifiedDate(String lastModifiedDate) { this.lastModifiedDate = lastModifiedDate; }

    public String getSystemModstamp() { return systemModstamp; }
    public void setSystemModstamp(String systemModstamp) { this.systemModstamp = systemModstamp; }

    public String getRawData() { return rawData; }
    public void setRawData(String rawData) { this.rawData = rawData; }

    public String getSyncedAt() { return syncedAt; }
    public void setSyncedAt(String syncedAt) { this.syncedAt = syncedAt; }

    public AccountEntity getAccount() { return account; }
    public void setAccount(AccountEntity account) { this.account = account; }

    public String getAccountId() {
        return account != null ? account.getId() : null;
    }

    public String getAccountName() {
        return account != null ? account.getName() : null;
    }
}
