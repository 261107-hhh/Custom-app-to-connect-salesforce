package com.salesforce.sync.model.dto;

public class AuthResponse {
    private boolean success;
    private String token;
    private String email;
    private String name;
    private String message;

    public AuthResponse() {}

    public AuthResponse(boolean success, String token, String email, String name, String message) {
        this.success = success;
        this.token = token;
        this.email = email;
        this.name = name;
        this.message = message;
    }

    public static AuthResponse success(String token, String email, String name, String message) {
        return new AuthResponse(true, token, email, name, message);
    }

    public static AuthResponse error(String message) {
        return new AuthResponse(false, null, null, null, message);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
