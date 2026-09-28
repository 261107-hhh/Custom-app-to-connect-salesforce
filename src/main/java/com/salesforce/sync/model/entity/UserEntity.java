package com.salesforce.sync.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_users", indexes = {
    @Index(name = "idx_user_email", columnList = "email", unique = true)
})
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<OrganizationMemberEntity> memberships = new java.util.ArrayList<>();

    public UserEntity() {}

    public UserEntity(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.createdAt = LocalDateTime.now();
    }

    @Column(name = "default_organization_id", length = 100)
    private String defaultOrganizationId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getDefaultOrganizationId() { return defaultOrganizationId; }
    public void setDefaultOrganizationId(String defaultOrganizationId) { this.defaultOrganizationId = defaultOrganizationId; }

    public java.util.List<OrganizationMemberEntity> getMemberships() { return memberships; }
    public void setMemberships(java.util.List<OrganizationMemberEntity> memberships) { this.memberships = memberships; }
}
