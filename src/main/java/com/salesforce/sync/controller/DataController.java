package com.salesforce.sync.controller;

import com.salesforce.sync.model.entity.*;
import com.salesforce.sync.repository.*;
import com.salesforce.sync.security.SecurityUtils;
import com.salesforce.sync.service.SalesforceClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/data")
@Tag(name = "Data Explorer & Creation", description = "Query local records, create new records with User Audit tracking, and explore relationships")
public class DataController {

    private final AccountRepository accountRepo;
    private final ContactRepository contactRepo;
    private final OpportunityRepository opportunityRepo;
    private final LeadRepository leadRepo;
    private final SalesforceClientService sfClient;
    private final ObjectMapper objectMapper;

    public DataController(AccountRepository accountRepo,
                          ContactRepository contactRepo,
                          OpportunityRepository opportunityRepo,
                          LeadRepository leadRepo,
                          SalesforceClientService sfClient,
                          ObjectMapper objectMapper) {
        this.accountRepo = accountRepo;
        this.contactRepo = contactRepo;
        this.opportunityRepo = opportunityRepo;
        this.leadRepo = leadRepo;
        this.sfClient = sfClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/tables")
    @Operation(summary = "List Synced Tables", description = "Returns summary of synced object tables and total record counts for authenticated user.")
    public ResponseEntity<?> getTables(Authentication authentication) {
        String userEmail = SecurityUtils.resolveUserEmail(authentication);
        long accCount = userEmail != null ? accountRepo.countByUser(userEmail) : 0L;
        long conCount = userEmail != null ? contactRepo.countByUser(userEmail) : 0L;
        long oppCount = userEmail != null ? opportunityRepo.countByUser(userEmail) : 0L;
        long leadCount = userEmail != null ? leadRepo.countByUser(userEmail) : 0L;

        List<Map<String, Object>> tables = List.of(
                Map.of("objectName", "Account", "tableName", "sf_account", "count", accCount),
                Map.of("objectName", "Contact", "tableName", "sf_contact", "count", conCount),
                Map.of("objectName", "Opportunity", "tableName", "sf_opportunity", "count", oppCount),
                Map.of("objectName", "Lead", "tableName", "sf_lead", "count", leadCount)
        );
        return ResponseEntity.ok(Map.of("success", true, "data", tables));
    }

    @GetMapping("/{objectName}")
    @Transactional(readOnly = true)
    @Operation(summary = "Query Object Records", description = "Returns paginated records with search, relational lookups, and user audit details.")
    public ResponseEntity<?> queryRecords(
            @PathVariable String objectName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false, defaultValue = "DESC") String sortOrder,
            Authentication authentication) {

        String userEmail = SecurityUtils.resolveUserEmail(authentication);
        if (userEmail == null || userEmail.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "records", List.of(),
                            "total", 0,
                            "page", page,
                            "limit", limit,
                            "totalPages", 0,
                            "columns", List.of()
                    )
            ));
        }

        int pageIndex = Math.max(0, page - 1);
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String safeSort = mapSortProperty(objectName, sortBy);
        Pageable pageable = PageRequest.of(pageIndex, limit, Sort.by(direction, safeSort));

        String lower = objectName.toLowerCase();
        List<Map<String, Object>> records = new ArrayList<>();
        long total = 0;
        int totalPages = 0;
        List<String> columns = new ArrayList<>();

        if ("account".equals(lower)) {
            Page<AccountEntity> accPage = accountRepo.searchAccountsByUser(userEmail, search, pageable);
            total = accPage.getTotalElements();
            totalPages = accPage.getTotalPages();
            columns = List.of("Id", "Name", "Type", "Industry", "Phone", "BillingCity", "custom_app_created_by", "synced_by");

            for (AccountEntity a : accPage.getContent()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("Id", a.getId());
                map.put("Name", a.getName());
                map.put("Type", a.getType());
                map.put("Industry", a.getIndustry());
                map.put("Phone", a.getPhone());
                map.put("BillingCity", a.getBillingCity());
                map.put("raw_data", a.getRawData());
                map.put("custom_app_created_by", a.getCustomAppCreatedBy());
                map.put("custom_app_modified_by", a.getCustomAppModifiedBy());
                map.put("is_custom_app_created", a.getIsCustomAppCreated());
                map.put("synced_by", a.getSyncedBy());
                map.put("_contact_count", a.getContacts() != null ? a.getContacts().stream().filter(c -> c.isAssociatedWithUser(userEmail)).count() : 0);
                map.put("_opportunity_count", a.getOpportunities() != null ? a.getOpportunities().stream().filter(o -> o.isAssociatedWithUser(userEmail)).count() : 0);
                records.add(map);
            }
        } else if ("contact".equals(lower)) {
            Page<ContactEntity> conPage = contactRepo.searchContactsByUser(userEmail, search, pageable);
            total = conPage.getTotalElements();
            totalPages = conPage.getTotalPages();
            columns = List.of("Id", "Name", "Title", "Email", "Phone", "Account_Name", "custom_app_created_by", "synced_by");

            for (ContactEntity c : conPage.getContent()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("Id", c.getId());
                map.put("Name", c.getName());
                map.put("Title", c.getTitle());
                map.put("Email", c.getEmail());
                map.put("Phone", c.getPhone());
                map.put("AccountId", c.getAccountId());
                map.put("Account_Name", c.getAccountName());
                map.put("raw_data", c.getRawData());
                map.put("custom_app_created_by", c.getCustomAppCreatedBy());
                map.put("custom_app_modified_by", c.getCustomAppModifiedBy());
                map.put("is_custom_app_created", c.getIsCustomAppCreated());
                map.put("synced_by", c.getSyncedBy());
                records.add(map);
            }
        } else if ("opportunity".equals(lower)) {
            Page<OpportunityEntity> oppPage = opportunityRepo.searchOpportunitiesByUser(userEmail, search, pageable);
            total = oppPage.getTotalElements();
            totalPages = oppPage.getTotalPages();
            columns = List.of("Id", "Name", "StageName", "Amount", "CloseDate", "Account_Name", "custom_app_created_by", "synced_by");

            for (OpportunityEntity o : oppPage.getContent()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("Id", o.getId());
                map.put("Name", o.getName());
                map.put("StageName", o.getStageName());
                map.put("Amount", o.getAmount());
                map.put("CloseDate", o.getCloseDate());
                map.put("AccountId", o.getAccountId());
                map.put("Account_Name", o.getAccountName());
                map.put("raw_data", o.getRawData());
                map.put("custom_app_created_by", o.getCustomAppCreatedBy());
                map.put("custom_app_modified_by", o.getCustomAppModifiedBy());
                map.put("is_custom_app_created", o.getIsCustomAppCreated());
                map.put("synced_by", o.getSyncedBy());
                records.add(map);
            }
        } else if ("lead".equals(lower)) {
            Page<LeadEntity> leadPage = leadRepo.searchLeadsByUser(userEmail, search, pageable);
            total = leadPage.getTotalElements();
            totalPages = leadPage.getTotalPages();
            columns = List.of("Id", "Name", "Company", "Email", "Phone", "Status", "custom_app_created_by", "synced_by");

            for (LeadEntity l : leadPage.getContent()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("Id", l.getId());
                map.put("Name", l.getName());
                map.put("Company", l.getCompany());
                map.put("Email", l.getEmail());
                map.put("Phone", l.getPhone());
                map.put("Status", l.getStatus());
                map.put("raw_data", l.getRawData());
                map.put("custom_app_created_by", l.getCustomAppCreatedBy());
                map.put("custom_app_modified_by", l.getCustomAppModifiedBy());
                map.put("is_custom_app_created", l.getIsCustomAppCreated());
                map.put("synced_by", l.getSyncedBy());
                records.add(map);
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "records", records,
                        "total", total,
                        "page", page,
                        "limit", limit,
                        "totalPages", totalPages,
                        "columns", columns
                )
        ));
    }

    @GetMapping("/{objectName}/{id}")
    @Operation(summary = "Get Record by ID", description = "Retrieves raw payload and fields for a specific record for authenticated user.")
    public ResponseEntity<?> getRecordById(@PathVariable String objectName, @PathVariable String id, Authentication authentication) {
        String userEmail = SecurityUtils.resolveUserEmail(authentication);
        if (userEmail == null || userEmail.isBlank()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Record not found"));
        }

        String lower = objectName.toLowerCase();
        Object record = null;
        if ("account".equals(lower)) record = accountRepo.findByIdAndUser(id, userEmail).orElse(null);
        else if ("contact".equals(lower)) record = contactRepo.findByIdAndUser(id, userEmail).orElse(null);
        else if ("opportunity".equals(lower)) record = opportunityRepo.findByIdAndUser(id, userEmail).orElse(null);
        else if ("lead".equals(lower)) record = leadRepo.findByIdAndUser(id, userEmail).orElse(null);

        if (record == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Record not found"));
        }
        return ResponseEntity.ok(Map.of("success", true, "data", record));
    }

    @PostMapping("/{objectName}/create")
    @Operation(summary = "Create Record in Salesforce with User Audit Tracking",
            description = "Creates record in Salesforce and saves in PostgreSQL with custom_app_created_by attributed to authenticated user.")
    public ResponseEntity<?> createRecord(
            @PathVariable String objectName,
            @RequestBody Map<String, Object> recordData,
            Authentication authentication) {
        try {
            if (recordData == null || recordData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "No field data provided."));
            }

            // 1. Identify authenticated user
            String userEmail = SecurityUtils.resolveUserEmail(authentication);
            if (userEmail == null || userEmail.isBlank()) {
                userEmail = "anonymous@app.local";
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> fieldsToSave = (recordData.containsKey("fields") && recordData.get("fields") instanceof Map)
                    ? (Map<String, Object>) recordData.get("fields")
                    : recordData;

            // 2. Push to Salesforce
            Map<String, Object> sfResult = sfClient.createRecord(objectName, fieldsToSave);
            String newId = (String) sfResult.get("id");

            // 3. Save locally in PostgreSQL with USER AUDIT TRACKING
            saveLocalWithAudit(objectName, newId, fieldsToSave, userEmail);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "id", newId,
                            "objectName", objectName,
                            "createdBy", userEmail,
                            "message", objectName + " record created successfully in Salesforce and attributed to " + userEmail
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/{objectName}/{id}/related")
    @Transactional(readOnly = true)
    @Operation(summary = "Get Relational Hierarchy", description = "Returns mapped Parent Account, related Contacts, and related Opportunities for authenticated user.")
    public ResponseEntity<?> getRelatedRecords(@PathVariable String objectName, @PathVariable String id, Authentication authentication) {
        String userEmail = SecurityUtils.resolveUserEmail(authentication);
        if (userEmail == null || userEmail.isBlank()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Record not found"));
        }

        String lower = objectName.toLowerCase();
        Map<String, Object> bundle = new HashMap<>();
        bundle.put("objectName", objectName);
        bundle.put("id", id);

        if ("account".equals(lower)) {
            Optional<AccountEntity> accOpt = accountRepo.findByIdAndUser(id, userEmail);
            if (accOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "error", "Account not found"));

            bundle.put("account", accOpt.get());
            bundle.put("contacts", contactRepo.findByAccountIdAndUser(id, userEmail));
            bundle.put("opportunities", opportunityRepo.findByAccountIdAndUser(id, userEmail));
        } else if ("contact".equals(lower)) {
            Optional<ContactEntity> conOpt = contactRepo.findByIdAndUser(id, userEmail);
            if (conOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "error", "Contact not found"));

            ContactEntity con = conOpt.get();
            bundle.put("contact", con);
            AccountEntity acc = con.getAccount();
            if (acc != null && acc.isAssociatedWithUser(userEmail)) {
                bundle.put("account", acc);
                bundle.put("opportunities", opportunityRepo.findByAccountIdAndUser(acc.getId(), userEmail));
            } else {
                bundle.put("account", null);
                bundle.put("opportunities", List.of());
            }
        } else if ("opportunity".equals(lower)) {
            Optional<OpportunityEntity> oppOpt = opportunityRepo.findByIdAndUser(id, userEmail);
            if (oppOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "error", "Opportunity not found"));

            OpportunityEntity opp = oppOpt.get();
            bundle.put("opportunity", opp);
            AccountEntity acc = opp.getAccount();
            if (acc != null && acc.isAssociatedWithUser(userEmail)) {
                bundle.put("account", acc);
                bundle.put("contacts", contactRepo.findByAccountIdAndUser(acc.getId(), userEmail));
            } else {
                bundle.put("account", null);
                bundle.put("contacts", List.of());
            }
        } else {
            Optional<LeadEntity> leadOpt = leadRepo.findByIdAndUser(id, userEmail);
            if (leadOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "error", "Lead not found"));
            bundle.put("record", leadOpt.get());
        }

        return ResponseEntity.ok(Map.of("success", true, "data", bundle));
    }

    private void saveLocalWithAudit(String objectName, String id, Map<String, Object> data, String userEmail) {
        String lower = objectName.toLowerCase();
        String nowStr = LocalDateTime.now().toString();
        String rawJson = "{}";
        try {
            rawJson = objectMapper.writeValueAsString(data);
        } catch (Exception ignored) {}

        if ("account".equals(lower)) {
            AccountEntity acc = accountRepo.findById(id).orElse(new AccountEntity());
            acc.setId(id);
            acc.setName((String) data.getOrDefault("Name", "New Account"));
            acc.setType((String) data.get("Type"));
            acc.setIndustry((String) data.get("Industry"));
            if (data.containsKey("AnnualRevenue") && data.get("AnnualRevenue") != null) {
                try {
                    acc.setAnnualRevenue(Double.valueOf(data.get("AnnualRevenue").toString()));
                } catch (Exception ignored) {}
            }
            acc.setPhone((String) data.get("Phone"));
            acc.setWebsite((String) data.get("Website"));
            acc.setBillingCity((String) data.get("BillingCity"));
            acc.setCreatedDate(nowStr);
            acc.setLastModifiedDate(nowStr);
            acc.setRawData(rawJson);
            acc.markCreatedByCustomApp(userEmail);
            accountRepo.save(acc);
        } else if ("contact".equals(lower)) {
            ContactEntity con = contactRepo.findById(id).orElse(new ContactEntity());
            con.setId(id);
            String fn = (String) data.getOrDefault("FirstName", "");
            String ln = (String) data.getOrDefault("LastName", "");
            con.setName((fn + " " + ln).trim());
            con.setFirstName(fn);
            con.setLastName(ln);
            con.setEmail((String) data.get("Email"));
            con.setPhone((String) data.get("Phone"));
            con.setTitle((String) data.get("Title"));
            con.setDepartment((String) data.get("Department"));
            con.setCreatedDate(nowStr);
            con.setLastModifiedDate(nowStr);
            con.setRawData(rawJson);

            String accId = (String) data.get("AccountId");
            if (accId != null && !accId.isBlank()) {
                accountRepo.findById(accId).ifPresent(con::setAccount);
            }
            con.markCreatedByCustomApp(userEmail);
            contactRepo.save(con);
        } else if ("opportunity".equals(lower)) {
            OpportunityEntity opp = opportunityRepo.findById(id).orElse(new OpportunityEntity());
            opp.setId(id);
            opp.setName((String) data.getOrDefault("Name", "New Opportunity"));
            opp.setStageName((String) data.get("StageName"));
            if (data.containsKey("Amount") && data.get("Amount") != null) {
                try {
                    opp.setAmount(Double.valueOf(data.get("Amount").toString()));
                } catch (Exception ignored) {}
            }
            if (data.containsKey("Probability") && data.get("Probability") != null) {
                try {
                    opp.setProbability(Double.valueOf(data.get("Probability").toString()));
                } catch (Exception ignored) {}
            }
            opp.setCloseDate((String) data.get("CloseDate"));
            opp.setType((String) data.get("Type"));
            opp.setCreatedDate(nowStr);
            opp.setLastModifiedDate(nowStr);
            opp.setRawData(rawJson);

            String accId = (String) data.get("AccountId");
            if (accId != null && !accId.isBlank()) {
                accountRepo.findById(accId).ifPresent(opp::setAccount);
            }
            opp.markCreatedByCustomApp(userEmail);
            opportunityRepo.save(opp);
        } else if ("lead".equals(lower)) {
            LeadEntity lead = leadRepo.findById(id).orElse(new LeadEntity());
            lead.setId(id);
            lead.setName((String) data.getOrDefault("Name", "New Lead"));
            lead.setCompany((String) data.get("Company"));
            lead.setEmail((String) data.get("Email"));
            lead.setPhone((String) data.get("Phone"));
            lead.setTitle((String) data.get("Title"));
            lead.setStatus((String) data.get("Status"));
            lead.setCreatedDate(nowStr);
            lead.setLastModifiedDate(nowStr);
            lead.setRawData(rawJson);
            lead.markCreatedByCustomApp(userEmail);
            leadRepo.save(lead);
        }
    }

    private String mapSortProperty(String objectName, String prop) {
        if ("Name".equalsIgnoreCase(prop)) return "name";
        if ("CreatedDate".equalsIgnoreCase(prop)) return "createdDate";
        if ("LastModifiedDate".equalsIgnoreCase(prop)) return "lastModifiedDate";
        return "id";
    }
}
