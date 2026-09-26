package com.salesforce.sync.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "_organization_invitations", indexes = {
    @Index(name = "idx_invitation_token", columnList = "token", unique = true),
    @Index(name = "idx_invitation_email_org", columnList = "email, organization_id")
})
public class OrganizationInvitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private OrganizationEntity organization;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 30)
    private String role = "MEMBER"; // ADMIN, MEMBER, READONLY

    @Column(nullable = false, unique = true, length = 120)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, length = 30)
    private String status = "PENDING"; // PENDING, ACCEPTED, EXPIRED, REVOKED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserEntity invitedByUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public OrganizationInvitationEntity() {}

    public OrganizationInvitationEntity(OrganizationEntity organization, String email, String role, String token, LocalDateTime expiresAt, UserEntity invitedByUser) {
        this.organization = organization;
        this.email = email != null ? email.trim().toLowerCase() : "";
        this.role = (role != null && !role.isBlank()) ? role.toUpperCase() : "MEMBER";
        this.token = token;
        this.expiresAt = expiresAt;
        this.status = "PENDING";
        this.invitedByUser = invitedByUser;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public OrganizationEntity getOrganization() { return organization; }
    public void setOrganization(OrganizationEntity organization) { this.organization = organization; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UserEntity getInvitedByUser() { return invitedByUser; }
    public void setInvitedByUser(UserEntity invitedByUser) { this.invitedByUser = invitedByUser; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
