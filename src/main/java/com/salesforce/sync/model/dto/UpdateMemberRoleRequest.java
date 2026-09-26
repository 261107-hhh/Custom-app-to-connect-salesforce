package com.salesforce.sync.model.dto;

public class UpdateMemberRoleRequest {
    private String role; // OWNER, ADMIN, MEMBER, READONLY

    public UpdateMemberRoleRequest() {}

    public UpdateMemberRoleRequest(String role) {
        this.role = role;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
