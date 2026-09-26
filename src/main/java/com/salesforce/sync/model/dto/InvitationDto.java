package com.salesforce.sync.model.dto;

import java.time.LocalDateTime;

public class InvitationDto {
    private Long id;
    private String email;
    private String role;
    private String token;
    private LocalDateTime expiresAt;
    private String status;
    private String invitedByEmail;
    private LocalDateTime createdAt;

    public InvitationDto() {}

    public InvitationDto(Long id, String email, String role, String token, LocalDateTime expiresAt, String status, String invitedByEmail, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.token = token;
        this.expiresAt = expiresAt;
        this.status = status;
        this.invitedByEmail = invitedByEmail;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public String getInvitedByEmail() { return invitedByEmail; }
    public void setInvitedByEmail(String invitedByEmail) { this.invitedByEmail = invitedByEmail; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
