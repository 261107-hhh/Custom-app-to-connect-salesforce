package com.salesforce.sync.controller;

import com.salesforce.sync.service.SalesforceClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Salesforce Connection", description = "Manage Salesforce OAuth and sandbox connections")
public class AuthController {

    private final SalesforceClientService sfClient;

    public AuthController(SalesforceClientService sfClient) {
        this.sfClient = sfClient;
    }

    @PostMapping("/connect")
    @Operation(summary = "Connect to Salesforce", description = "Authenticate via External Client App (Client Credentials), Username/Password, or Mock Mode.")
    public ResponseEntity<?> connect(@RequestBody Map<String, Object> credentials) {
        try {
            Map<String, Object> result = sfClient.connect(credentials);
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/disconnect")
    @Operation(summary = "Disconnect Salesforce", description = "Clears active session credentials.")
    public ResponseEntity<?> disconnect() {
        sfClient.disconnect();
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("message", "Disconnected from Salesforce.")));
    }

    @GetMapping("/status")
    @Operation(summary = "Salesforce Connection Status", description = "Returns whether the application is connected to a live Salesforce org or mock sandbox.")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of("success", true, "data", sfClient.getStatus()));
    }
}
