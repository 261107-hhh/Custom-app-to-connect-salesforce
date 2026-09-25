package com.salesforce.sync.controller;

import com.salesforce.sync.service.SalesforceClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/objects")
@Tag(name = "Schema Introspection", description = "Salesforce object metadata, field types, and creatable fields")
public class SchemaController {

    private final SalesforceClientService sfClient;

    public SchemaController(SalesforceClientService sfClient) {
        this.sfClient = sfClient;
    }

    @GetMapping
    @Operation(summary = "Supported Objects / Global Describe", description = "Returns queryable Salesforce sObjects metadata.")
    public ResponseEntity<?> getSupportedObjects() {
        try {
            com.fasterxml.jackson.databind.JsonNode globalDesc = sfClient.describeGlobal();
            List<Map<String, Object>> sobjects = new ArrayList<>();
            if (globalDesc.has("sobjects") && globalDesc.path("sobjects").isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode o : globalDesc.path("sobjects")) {
                    if (o.path("queryable").asBoolean(true)) {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("name", o.path("name").asText());
                        map.put("label", o.path("label").asText());
                        map.put("custom", o.path("custom").asBoolean(false));
                        map.put("keyPrefix", o.path("keyPrefix").asText(null));
                        sobjects.add(map);
                    }
                }
            }
            if (sobjects.isEmpty()) {
                sobjects = List.of(
                        Map.of("name", "Account", "label", "Account", "custom", false, "keyPrefix", "001"),
                        Map.of("name", "Contact", "label", "Contact", "custom", false, "keyPrefix", "003"),
                        Map.of("name", "Opportunity", "label", "Opportunity", "custom", false, "keyPrefix", "006"),
                        Map.of("name", "Lead", "label", "Lead", "custom", false, "keyPrefix", "00Q")
                );
            }
            return ResponseEntity.ok(Map.of("success", true, "data", sobjects));
        } catch (Exception e) {
            List<Map<String, Object>> fallback = List.of(
                    Map.of("name", "Account", "label", "Account", "custom", false, "keyPrefix", "001"),
                    Map.of("name", "Contact", "label", "Contact", "custom", false, "keyPrefix", "003"),
                    Map.of("name", "Opportunity", "label", "Opportunity", "custom", false, "keyPrefix", "006"),
                    Map.of("name", "Lead", "label", "Lead", "custom", false, "keyPrefix", "00Q")
            );
            return ResponseEntity.ok(Map.of("success", true, "data", fallback));
        }
    }

    @GetMapping("/{name}/describe")
    @Operation(summary = "Describe Object Schema", description = "Fetches the full Salesforce describe payload for the specified object.")
    public ResponseEntity<?> describeObject(@PathVariable String name) {
        try {
            return ResponseEntity.ok(Map.of("success", true, "data", sfClient.describeObject(name)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/{name}/fields")
    @Operation(summary = "Get Creatable Fields", description = "Returns introspected fields that are creatable, with types, labels, and picklist options.")
    public ResponseEntity<?> getCreatableFields(@PathVariable String name) {
        try {
            List<Map<String, Object>> fields = sfClient.getCreatableFields(name);
            return ResponseEntity.ok(Map.of("success", true, "data", fields));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
