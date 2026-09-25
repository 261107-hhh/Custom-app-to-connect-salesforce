package com.salesforce.sync.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "sf_lead", indexes = {
    @Index(name = "idx_lead_name", columnList = "name")
})
public class LeadEntity extends BaseAuditableEntity {

    @Id
    @Column(length = 30)
    private String id;

    @Column(length = 255)
    private String name;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(length = 255)
    private String company;

    @Column(length = 150)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(length = 100)
    private String title;

    @Column(length = 100)
    private String status;

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

    public LeadEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

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
}
