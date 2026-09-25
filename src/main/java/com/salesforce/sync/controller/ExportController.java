package com.salesforce.sync.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.sync.model.entity.*;
import com.salesforce.sync.repository.*;
import com.salesforce.sync.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/export")
@Tag(name = "Data Export", description = "Export synchronized records in CSV or JSON format")
public class ExportController {

    private final AccountRepository accountRepo;
    private final ContactRepository contactRepo;
    private final OpportunityRepository opportunityRepo;
    private final LeadRepository leadRepo;
    private final ObjectMapper objectMapper;

    public ExportController(AccountRepository accountRepo,
                            ContactRepository contactRepo,
                            OpportunityRepository opportunityRepo,
                            LeadRepository leadRepo,
                            ObjectMapper objectMapper) {
        this.accountRepo = accountRepo;
        this.contactRepo = contactRepo;
        this.opportunityRepo = opportunityRepo;
        this.leadRepo = leadRepo;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{objectName}")
    @Transactional(readOnly = true)
    @Operation(summary = "Export Object Records", description = "Exports all records of the specified object for authenticated user as CSV or JSON.")
    public ResponseEntity<?> exportRecords(
            @PathVariable String objectName,
            @RequestParam(defaultValue = "json") String format,
            Authentication authentication) {

        String userEmail = SecurityUtils.resolveUserEmail(authentication);
        String lower = objectName.toLowerCase();
        List<Map<String, Object>> records = new ArrayList<>();

        if (userEmail == null || userEmail.isBlank()) {
            return generateExportResponse(lower, format, records);
        }

        if ("account".equals(lower)) {
            List<AccountEntity> accounts = accountRepo.findAllByUser(userEmail);
            for (AccountEntity a : accounts) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("Id", a.getId());
                map.put("Name", a.getName());
                map.put("Type", a.getType());
                map.put("Industry", a.getIndustry());
                map.put("AnnualRevenue", a.getAnnualRevenue());
                map.put("Phone", a.getPhone());
                map.put("Website", a.getWebsite());
                map.put("BillingCity", a.getBillingCity());
                map.put("CreatedDate", a.getCreatedDate());
                map.put("LastModifiedDate", a.getLastModifiedDate());
                map.put("SystemModstamp", a.getSystemModstamp());
                map.put("custom_app_created_by", a.getCustomAppCreatedBy());
                map.put("custom_app_modified_by", a.getCustomAppModifiedBy());
                map.put("is_custom_app_created", a.getIsCustomAppCreated());
                map.put("synced_by", a.getSyncedBy());
                map.put("raw_data", a.getRawData());
                records.add(map);
            }
        } else if ("contact".equals(lower)) {
            List<ContactEntity> contacts = contactRepo.findAllByUser(userEmail);
            for (ContactEntity c : contacts) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("Id", c.getId());
                map.put("Name", c.getName());
                map.put("FirstName", c.getFirstName());
                map.put("LastName", c.getLastName());
                map.put("Email", c.getEmail());
                map.put("Phone", c.getPhone());
                map.put("Title", c.getTitle());
                map.put("Department", c.getDepartment());
                map.put("AccountId", c.getAccountId());
                map.put("Account_Name", c.getAccountName());
                map.put("CreatedDate", c.getCreatedDate());
                map.put("LastModifiedDate", c.getLastModifiedDate());
                map.put("SystemModstamp", c.getSystemModstamp());
                map.put("custom_app_created_by", c.getCustomAppCreatedBy());
                map.put("custom_app_modified_by", c.getCustomAppModifiedBy());
                map.put("is_custom_app_created", c.getIsCustomAppCreated());
                map.put("synced_by", c.getSyncedBy());
                map.put("raw_data", c.getRawData());
                records.add(map);
            }
        } else if ("opportunity".equals(lower)) {
            List<OpportunityEntity> opps = opportunityRepo.findAllByUser(userEmail);
            for (OpportunityEntity o : opps) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("Id", o.getId());
                map.put("Name", o.getName());
                map.put("StageName", o.getStageName());
                map.put("Amount", o.getAmount());
                map.put("CloseDate", o.getCloseDate());
                map.put("Probability", o.getProbability());
                map.put("Type", o.getType());
                map.put("AccountId", o.getAccountId());
                map.put("Account_Name", o.getAccountName());
                map.put("CreatedDate", o.getCreatedDate());
                map.put("LastModifiedDate", o.getLastModifiedDate());
                map.put("SystemModstamp", o.getSystemModstamp());
                map.put("custom_app_created_by", o.getCustomAppCreatedBy());
                map.put("custom_app_modified_by", o.getCustomAppModifiedBy());
                map.put("is_custom_app_created", o.getIsCustomAppCreated());
                map.put("synced_by", o.getSyncedBy());
                map.put("raw_data", o.getRawData());
                records.add(map);
            }
        } else if ("lead".equals(lower)) {
            List<LeadEntity> leads = leadRepo.findAllByUser(userEmail);
            for (LeadEntity l : leads) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("Id", l.getId());
                map.put("Name", l.getName());
                map.put("FirstName", l.getFirstName());
                map.put("LastName", l.getLastName());
                map.put("Company", l.getCompany());
                map.put("Email", l.getEmail());
                map.put("Phone", l.getPhone());
                map.put("Title", l.getTitle());
                map.put("Status", l.getStatus());
                map.put("CreatedDate", l.getCreatedDate());
                map.put("LastModifiedDate", l.getLastModifiedDate());
                map.put("SystemModstamp", l.getSystemModstamp());
                map.put("custom_app_created_by", l.getCustomAppCreatedBy());
                map.put("custom_app_modified_by", l.getCustomAppModifiedBy());
                map.put("is_custom_app_created", l.getIsCustomAppCreated());
                map.put("synced_by", l.getSyncedBy());
                map.put("raw_data", l.getRawData());
                records.add(map);
            }
        }

        return generateExportResponse(lower, format, records);
    }

    private ResponseEntity<?> generateExportResponse(String lower, String format, List<Map<String, Object>> records) {
        String canonicalName = Character.toUpperCase(lower.charAt(0)) + lower.substring(1);

        if ("csv".equalsIgnoreCase(format)) {
            if (records.isEmpty()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + canonicalName + "_export.csv\"")
                        .body("Id\n");
            }

            // Exclude raw_data for clean readable CSV
            List<String> headers = records.get(0).keySet().stream()
                    .filter(k -> !"raw_data".equals(k))
                    .toList();

            StringBuilder csv = new StringBuilder();
            csv.append(String.join(",", headers)).append("\n");

            for (Map<String, Object> row : records) {
                List<String> escapedValues = new ArrayList<>();
                for (String header : headers) {
                    Object val = row.get(header);
                    String strVal = val == null ? "" : String.valueOf(val);
                    escapedValues.add("\"" + strVal.replace("\"", "\"\"") + "\"");
                }
                csv.append(String.join(",", escapedValues)).append("\n");
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + canonicalName + "_export.csv\"")
                    .body(csv.toString());
        }

        // Default JSON export - unpack raw_data if valid JSON
        List<Object> sanitized = new ArrayList<>();
        for (Map<String, Object> r : records) {
            String rawJson = (String) r.get("raw_data");
            if (rawJson != null && !rawJson.isBlank()) {
                try {
                    sanitized.add(objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {}));
                    continue;
                } catch (Exception ignored) {}
            }
            Map<String, Object> copy = new LinkedHashMap<>(r);
            copy.remove("raw_data");
            sanitized.add(copy);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + canonicalName + "_export.json\"")
                .body(sanitized);
    }
}
