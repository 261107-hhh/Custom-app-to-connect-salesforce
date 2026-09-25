package com.salesforce.sync.controller;

import com.salesforce.sync.service.MockSalesforceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/mock")
@Tag(name = "Mock Sandbox Simulator", description = "Endpoints for simulating Salesforce events and delta changes")
public class MockController {

    private final MockSalesforceService mockSalesforceService;

    public MockController(MockSalesforceService mockSalesforceService) {
        this.mockSalesforceService = mockSalesforceService;
    }

    @PostMapping("/simulate-update")
    @Operation(summary = "Simulate Remote Salesforce Update", description = "Generates a new or updated record in mock Salesforce to test incremental sync.")
    public ResponseEntity<?> simulateUpdate(@RequestBody(required = false) Map<String, Object> body) {
        String objectName = "Account";
        if (body != null && body.containsKey("objectName") && body.get("objectName") != null) {
            objectName = body.get("objectName").toString();
        }

        Map<String, Object> record = mockSalesforceService.simulateNewOrModifiedRecord(objectName);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Simulated a new/updated " + objectName + " in mock Salesforce. You can now run incremental sync!",
                "record", record
        ));
    }
}
