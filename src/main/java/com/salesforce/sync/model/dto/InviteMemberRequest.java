package com.salesforce.sync.model.dto;

public class InviteMemberRequest {
    private String email;
    private String role; // ADMIN, MEMBER, READONLY

    public InviteMemberRequest() {}

    public InviteMemberRequest(String email, String role) {
        this.email = email;
        this.role = role;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
