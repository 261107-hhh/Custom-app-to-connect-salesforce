package com.salesforce.sync.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sf_account", indexes = {
    @Index(name = "idx_account_name", columnList = "name"),
    @Index(name = "idx_account_lastmod", columnList = "last_modified_date")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AccountEntity extends BaseAuditableEntity {

    @Id
    @Column(length = 30)
    private String id;

    @Column(length = 255)
    private String name;

    @Column(length = 100)
    private String type;

    @Column(length = 100)
    private String industry;

    @Column(name = "annual_revenue")
    private Double annualRevenue;

    @Column(length = 50)
    private String phone;

    @Column(length = 255)
    private String website;

    @Column(name = "billing_city", length = 100)
    private String billingCity;

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

    @JsonIgnore
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ContactEntity> contacts = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OpportunityEntity> opportunities = new ArrayList<>();

    public AccountEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public Double getAnnualRevenue() { return annualRevenue; }
    public void setAnnualRevenue(Double annualRevenue) { this.annualRevenue = annualRevenue; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public String getBillingCity() { return billingCity; }
    public void setBillingCity(String billingCity) { this.billingCity = billingCity; }

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

    public List<ContactEntity> getContacts() { return contacts; }
    public void setContacts(List<ContactEntity> contacts) { this.contacts = contacts; }

    public List<OpportunityEntity> getOpportunities() { return opportunities; }
    public void setOpportunities(List<OpportunityEntity> opportunities) { this.opportunities = opportunities; }
}
