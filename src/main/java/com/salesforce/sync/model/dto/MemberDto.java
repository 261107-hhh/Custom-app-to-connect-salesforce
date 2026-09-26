package com.salesforce.sync.model.dto;

import java.time.LocalDateTime;

public class MemberDto {
    private Long id;
    private Long userId;
    private String email;
    private String name;
    private String role;
    private String status;
    private LocalDateTime joinedAt;

    public MemberDto() {}

    public MemberDto(Long id, Long userId, String email, String name, String role, String status, LocalDateTime joinedAt) {
        this.id = id;
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.role = role;
        this.status = status;
        this.joinedAt = joinedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
}
