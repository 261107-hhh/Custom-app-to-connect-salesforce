package com.salesforce.sync.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "_organization_members", indexes = {
    @Index(name = "idx_member_user_org", columnList = "user_id, organization_id", unique = true),
    @Index(name = "idx_member_org", columnList = "organization_id")
})
public class OrganizationMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private OrganizationEntity organization;

    @Column(name = "role", nullable = false, length = 30)
    private String role = "MEMBER"; // OWNER, ADMIN, MEMBER, READONLY

    @Column(name = "status", nullable = false, length = 30)
    private String status = "ACTIVE"; // ACTIVE, SUSPENDED

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();

    public OrganizationMemberEntity() {}

    public OrganizationMemberEntity(UserEntity user, OrganizationEntity organization, String role) {
        this.user = user;
        this.organization = organization;
        this.role = (role != null && !role.isBlank()) ? role.toUpperCase() : "MEMBER";
        this.status = "ACTIVE";
        this.joinedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }

    public OrganizationEntity getOrganization() { return organization; }
    public void setOrganization(OrganizationEntity organization) { this.organization = organization; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }

    public boolean isAdminOrOwner() {
        return "OWNER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
    }
}
