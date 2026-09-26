package com.salesforce.sync.model.dto;

public class AcceptInviteRequest {
    private String token;
    private String name;     // Required if new user
    private String password; // Required if new user

    public AcceptInviteRequest() {}

    public AcceptInviteRequest(String token, String name, String password) {
        this.token = token;
        this.name = name;
        this.password = password;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
