package com.salesforce.sync.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.sync.model.entity.*;
import com.salesforce.sync.repository.*;
import com.salesforce.sync.security.SecurityUtils;
import com.salesforce.sync.service.SalesforceClientService;
import com.salesforce.sync.service.SalesforceSchemaRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import com.salesforce.sync.multitenancy.OrganizationContext;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/data")
@Tag(name = "Data Explorer & Creation", description = "Query local records, create new records with User Audit tracking, and explore relationships")
public class DataController {

    private static final Logger log = LoggerFactory.getLogger(DataController.class);

    private final AccountRepository accountRepo;
    private final ContactRepository contactRepo;
    private final OpportunityRepository opportunityRepo;
    private final LeadRepository leadRepo;
    private final SalesforceClientService sfClient;
    private final ObjectMapper objectMapper;
    private final SalesforceSchemaRegistry schemaRegistry;
    private final OrganizationMemberRepository memberRepo;

    public DataController(AccountRepository accountRepo,
                          ContactRepository contactRepo,
                          OpportunityRepository opportunityRepo,
                          LeadRepository leadRepo,
                          SalesforceClientService sfClient,
                          ObjectMapper objectMapper) {
        this(accountRepo, contactRepo, opportunityRepo, leadRepo, sfClient, objectMapper, new SalesforceSchemaRegistry(), null);
    }

    public DataController(AccountRepository accountRepo,
                          ContactRepository contactRepo,
                          OpportunityRepository opportunityRepo,
                          LeadRepository leadRepo,
                          SalesforceClientService sfClient,
                          ObjectMapper objectMapper,
                          SalesforceSchemaRegistry schemaRegistry) {
        this(accountRepo, contactRepo, opportunityRepo, leadRepo, sfClient, objectMapper, schemaRegistry, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DataController(AccountRepository accountRepo,
                          ContactRepository contactRepo,
                          OpportunityRepository opportunityRepo,
                          LeadRepository leadRepo,
                          SalesforceClientService sfClient,
                          ObjectMapper objectMapper,
                          SalesforceSchemaRegistry schemaRegistry,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) OrganizationMemberRepository memberRepo) {
        this.accountRepo = accountRepo;
        this.contactRepo = contactRepo;
        this.opportunityRepo = opportunityRepo;
        this.leadRepo = leadRepo;
        this.sfClient = sfClient;
        this.objectMapper = objectMapper;
        this.schemaRegistry = schemaRegistry != null ? schemaRegistry : new SalesforceSchemaRegistry();
        this.memberRepo = memberRepo;
    }

    @GetMapping("/tables")
    @Operation(summary = "List Synced Tables", description = "Returns summary of synced object tables and total record counts for authenticated user.")
    public ResponseEntity<?> getTables(Authentication authentication) {
        String userEmail = SecurityUtils.resolveUserEmail(authentication);
        long accCount = (userEmail != null && !userEmail.isBlank()) ? accountRepo.countByUser(userEmail) : accountRepo.count();
        long conCount = (userEmail != null && !userEmail.isBlank()) ? contactRepo.countByUser(userEmail) : contactRepo.count();
        long oppCount = (userEmail != null && !userEmail.isBlank()) ? opportunityRepo.countByUser(userEmail) : opportunityRepo.count();
        long leadCount = (userEmail != null && !userEmail.isBlank()) ? leadRepo.countByUser(userEmail) : leadRepo.count();

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
    @Operation(summary = "Query Object Records", description = "Returns paginated records with search, relational lookups, and all fields including null or empty values.")
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

            for (AccountEntity a : accPage.getContent()) {
                Map<String, Object> map = convertEntityToDetailMap(a);
                map.put("_contact_count", a.getContacts() != null ? a.getContacts().stream().filter(c -> c.isAssociatedWithUser(userEmail)).count() : 0);
                map.put("_opportunity_count", a.getOpportunities() != null ? a.getOpportunities().stream().filter(o -> o.isAssociatedWithUser(userEmail)).count() : 0);
                records.add(map);
            }
            columns = List.of("Id", "Name", "Type", "Industry", "Phone", "BillingCity", "AnnualRevenue", "Website", "custom_app_created_by", "synced_by");

        } else if ("contact".equals(lower)) {
            Page<ContactEntity> conPage = contactRepo.searchContactsByUser(userEmail, search, pageable);
            total = conPage.getTotalElements();
            totalPages = conPage.getTotalPages();

            for (ContactEntity c : conPage.getContent()) {
                records.add(convertEntityToDetailMap(c));
            }
            columns = List.of("Id", "Name", "Title", "Email", "Phone", "Department", "Account_Name", "custom_app_created_by", "synced_by");

        } else if ("opportunity".equals(lower)) {
            Page<OpportunityEntity> oppPage = opportunityRepo.searchOpportunitiesByUser(userEmail, search, pageable);
            total = oppPage.getTotalElements();
            totalPages = oppPage.getTotalPages();

            for (OpportunityEntity o : oppPage.getContent()) {
                records.add(convertEntityToDetailMap(o));
            }
            columns = List.of("Id", "Name", "StageName", "Amount", "CloseDate", "Probability", "Type", "Account_Name", "custom_app_created_by", "synced_by");

        } else if ("lead".equals(lower)) {
            Page<LeadEntity> leadPage = leadRepo.searchLeadsByUser(userEmail, search, pageable);
            total = leadPage.getTotalElements();
            totalPages = leadPage.getTotalPages();

            for (LeadEntity l : leadPage.getContent()) {
                records.add(convertEntityToDetailMap(l));
            }
            columns = List.of("Id", "Name", "Company", "Status", "Title", "Email", "Phone", "custom_app_created_by", "synced_by");
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
    @Operation(summary = "Get Record by ID", description = "Retrieves complete record details with all standard and custom fields including null or empty values.")
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
        Map<String, Object> detailMap = convertEntityToDetailMap(record);
        return ResponseEntity.ok(Map.of("success", true, "data", detailMap));
    }

    @PostMapping("/{objectName}/create")
    @Operation(summary = "Create Record in Salesforce with User Audit Tracking",
            description = "Creates record in Salesforce and saves in PostgreSQL with custom_app_created_by attributed to authenticated user.")
    public ResponseEntity<?> createRecord(
            @PathVariable String objectName,
            @RequestBody Map<String, Object> recordData,
            Authentication authentication) {
        try {
            if (isReadOnlyUser(authentication)) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "error", "Permission Denied: Users with READONLY role cannot create records."
                ));
            }

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

            // 2. Push to Salesforce if connected
            String newId = null;
            if (sfClient.isConnected()) {
                try {
                    Map<String, Object> sfResult = sfClient.createRecord(objectName, fieldsToSave);
                    newId = (String) sfResult.get("id");
                } catch (Exception sfEx) {
                    log.warn("[DataController] Salesforce live create failed for " + objectName + ": " + sfEx.getMessage());
                }
            }
            if (newId == null || newId.isBlank()) {
                newId = "LOCAL_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 15);
            }

            // 3. Save locally in PostgreSQL with USER AUDIT TRACKING
            saveLocalWithAudit(objectName, newId, fieldsToSave, userEmail);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "id", newId,
                            "objectName", objectName,
                            "createdBy", userEmail,
                            "message", objectName + " record created successfully!"
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/{objectName}/{id}/related")
    @Transactional(readOnly = true)
    @Operation(summary = "Get Relational Hierarchy", description = "Fetches related child and parent records for 360 degree relational view.")
    public ResponseEntity<?> getRelatedRecords(
            @PathVariable String objectName,
            @PathVariable String id,
            Authentication authentication) {
        String userEmail = SecurityUtils.resolveUserEmail(authentication);
        if (userEmail == null || userEmail.isBlank()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Record not found"));
        }

        String lower = objectName.toLowerCase();
        Map<String, Object> bundle = new LinkedHashMap<>();

        if ("account".equals(lower)) {
            Optional<AccountEntity> accOpt = accountRepo.findByIdAndUser(id, userEmail);
            if (accOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "error", "Account not found"));

            bundle.put("account", convertEntityToDetailMap(accOpt.get()));
            bundle.put("contacts", contactRepo.findByAccountIdAndUser(id, userEmail));
            bundle.put("opportunities", opportunityRepo.findByAccountIdAndUser(id, userEmail));
        } else if ("contact".equals(lower)) {
            Optional<ContactEntity> conOpt = contactRepo.findByIdAndUser(id, userEmail);
            if (conOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "error", "Contact not found"));

            ContactEntity con = conOpt.get();
            bundle.put("contact", convertEntityToDetailMap(con));
            AccountEntity acc = con.getAccount();
            if (acc != null && acc.isAssociatedWithUser(userEmail)) {
                bundle.put("account", convertEntityToDetailMap(acc));
                bundle.put("opportunities", opportunityRepo.findByAccountIdAndUser(acc.getId(), userEmail));
            } else {
                bundle.put("account", null);
                bundle.put("opportunities", List.of());
            }
        } else if ("opportunity".equals(lower)) {
            Optional<OpportunityEntity> oppOpt = opportunityRepo.findByIdAndUser(id, userEmail);
            if (oppOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "error", "Opportunity not found"));

            OpportunityEntity opp = oppOpt.get();
            bundle.put("opportunity", convertEntityToDetailMap(opp));
            AccountEntity acc = opp.getAccount();
            if (acc != null && acc.isAssociatedWithUser(userEmail)) {
                bundle.put("account", convertEntityToDetailMap(acc));
                bundle.put("contacts", contactRepo.findByAccountIdAndUser(acc.getId(), userEmail));
            } else {
                bundle.put("account", null);
                bundle.put("contacts", List.of());
            }
        } else {
            Optional<LeadEntity> leadOpt = leadRepo.findByIdAndUser(id, userEmail);
            if (leadOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "error", "Lead not found"));
            bundle.put("record", convertEntityToDetailMap(leadOpt.get()));
        }

        return ResponseEntity.ok(Map.of("success", true, "data", bundle));
    }

    @PutMapping("/{objectName}/{id}")
    @Operation(summary = "Update Record in Salesforce and Local Database", description = "Updates fields in Salesforce and stamps custom_app_modified_by with authenticated user.")
    public ResponseEntity<?> updateRecord(
            @PathVariable String objectName,
            @PathVariable String id,
            @RequestBody Map<String, Object> recordData,
            Authentication authentication) {
        try {
            if (isReadOnlyUser(authentication)) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "error", "Permission Denied: Users with READONLY role cannot edit records."
                ));
            }

            if (recordData == null || recordData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "No field data provided for update."));
            }

            String userEmail = SecurityUtils.resolveUserEmail(authentication);
            if (userEmail == null || userEmail.isBlank()) {
                userEmail = "anonymous@app.local";
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> fieldsToUpdate = (recordData.containsKey("fields") && recordData.get("fields") instanceof Map)
                    ? (Map<String, Object>) recordData.get("fields")
                    : recordData;

            // Remove non-updatable audit or read-only fields
            fieldsToUpdate.remove("id");
            fieldsToUpdate.remove("Id");
            fieldsToUpdate.remove("CreatedDate");
            fieldsToUpdate.remove("createdDate");
            fieldsToUpdate.remove("LastModifiedDate");
            fieldsToUpdate.remove("lastModifiedDate");
            fieldsToUpdate.remove("SystemModstamp");
            fieldsToUpdate.remove("systemModstamp");
            fieldsToUpdate.remove("custom_app_created_by");
            fieldsToUpdate.remove("custom_app_created_at");
            fieldsToUpdate.remove("custom_app_modified_by");
            fieldsToUpdate.remove("custom_app_modified_at");
            fieldsToUpdate.remove("is_custom_app_created");
            fieldsToUpdate.remove("synced_by");
            fieldsToUpdate.remove("organization_id");
            fieldsToUpdate.remove("raw_data");
            fieldsToUpdate.remove("Account_Name");
            fieldsToUpdate.remove("_contact_count");
            fieldsToUpdate.remove("_opportunity_count");

            // 1. Update in Salesforce if connected
            if (sfClient.isConnected()) {
                try {
                    Map<String, Object> sfPayload = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> entry : fieldsToUpdate.entrySet()) {
                        Object v = entry.getValue();
                        sfPayload.put(entry.getKey(), "".equals(v) ? null : v);
                    }
                    sfClient.updateRecord(objectName, id, sfPayload);
                } catch (Exception sfEx) {
                    log.warn("[DataController] Salesforce live update failed for " + objectName + " " + id + ": " + sfEx.getMessage());
                }
            }

            // 2. Always update locally with audit tracking and preserve all fields
            updateLocalWithAudit(objectName, id, fieldsToUpdate, userEmail);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "id", id,
                            "objectName", objectName,
                            "modifiedBy", userEmail,
                            "message", objectName + " record updated successfully!"
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private void updateLocalWithAudit(String objectName, String id, Map<String, Object> data, String userEmail) {
        String lower = objectName.toLowerCase();
        String nowStr = LocalDateTime.now().toString();

        if ("account".equals(lower)) {
            accountRepo.findById(id).ifPresent(acc -> {
                if (data.containsKey("Name")) acc.setName(toNullIfBlank(data.get("Name")));
                if (data.containsKey("Type")) acc.setType(toNullIfBlank(data.get("Type")));
                if (data.containsKey("Industry")) acc.setIndustry(toNullIfBlank(data.get("Industry")));
                if (data.containsKey("Phone")) acc.setPhone(toNullIfBlank(data.get("Phone")));
                if (data.containsKey("Website")) acc.setWebsite(toNullIfBlank(data.get("Website")));
                if (data.containsKey("BillingCity")) acc.setBillingCity(toNullIfBlank(data.get("BillingCity")));
                if (data.containsKey("AnnualRevenue")) {
                    Object v = data.get("AnnualRevenue");
                    if (v == null || v.toString().isBlank()) {
                        acc.setAnnualRevenue(null);
                    } else {
                        try {
                            acc.setAnnualRevenue(Double.valueOf(v.toString()));
                        } catch (Exception ignored) {}
                    }
                }
                acc.setLastModifiedDate(nowStr);
                acc.markModifiedByCustomApp(userEmail);
                try {
                    Map<String, Object> raw = (acc.getRawData() != null && !acc.getRawData().isBlank())
                            ? objectMapper.readValue(acc.getRawData(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {})
                            : new LinkedHashMap<>();
                    raw.putAll(data);
                    Map<String, Object> normalized = schemaRegistry.normalizeRecord("Account", raw, null);
                    acc.setRawData(objectMapper.writeValueAsString(normalized));
                } catch (Exception ignored) {}
                accountRepo.save(acc);
            });
        } else if ("contact".equals(lower)) {
            contactRepo.findById(id).ifPresent(con -> {
                String fn = data.containsKey("FirstName") ? toNullIfBlank(data.get("FirstName")) : con.getFirstName();
                String ln = data.containsKey("LastName") ? toNullIfBlank(data.get("LastName")) : con.getLastName();
                if (data.containsKey("FirstName")) con.setFirstName(fn);
                if (data.containsKey("LastName")) con.setLastName(ln);
                con.setName((Objects.toString(fn, "") + " " + Objects.toString(ln, "")).trim());
                if (data.containsKey("Email")) con.setEmail(toNullIfBlank(data.get("Email")));
                if (data.containsKey("Phone")) con.setPhone(toNullIfBlank(data.get("Phone")));
                if (data.containsKey("Title")) con.setTitle(toNullIfBlank(data.get("Title")));
                if (data.containsKey("Department")) con.setDepartment(toNullIfBlank(data.get("Department")));
                if (data.containsKey("AccountId")) {
                    String accId = (String) data.get("AccountId");
                    if (accId != null && !accId.isBlank()) {
                        accountRepo.findById(accId).ifPresent(con::setAccount);
                    } else {
                        con.setAccount(null);
                    }
                }
                con.setLastModifiedDate(nowStr);
                con.markModifiedByCustomApp(userEmail);
                try {
                    Map<String, Object> raw = (con.getRawData() != null && !con.getRawData().isBlank())
                            ? objectMapper.readValue(con.getRawData(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {})
                            : new LinkedHashMap<>();
                    raw.putAll(data);
                    Map<String, Object> normalized = schemaRegistry.normalizeRecord("Contact", raw, null);
                    con.setRawData(objectMapper.writeValueAsString(normalized));
                } catch (Exception ignored) {}
                contactRepo.save(con);
            });
        } else if ("opportunity".equals(lower)) {
            opportunityRepo.findById(id).ifPresent(opp -> {
                if (data.containsKey("Name")) opp.setName(toNullIfBlank(data.get("Name")));
                if (data.containsKey("StageName")) opp.setStageName(toNullIfBlank(data.get("StageName")));
                if (data.containsKey("Type")) opp.setType(toNullIfBlank(data.get("Type")));
                if (data.containsKey("CloseDate")) opp.setCloseDate(toNullIfBlank(data.get("CloseDate")));
                if (data.containsKey("Amount")) {
                    Object v = data.get("Amount");
                    if (v == null || v.toString().isBlank()) {
                        opp.setAmount(null);
                    } else {
                        try {
                            opp.setAmount(Double.valueOf(v.toString()));
                        } catch (Exception ignored) {}
                    }
                }
                if (data.containsKey("Probability")) {
                    Object v = data.get("Probability");
                    if (v == null || v.toString().isBlank()) {
                        opp.setProbability(null);
                    } else {
                        try {
                            opp.setProbability(Double.valueOf(v.toString()));
                        } catch (Exception ignored) {}
                    }
                }
                if (data.containsKey("AccountId")) {
                    String accId = (String) data.get("AccountId");
                    if (accId != null && !accId.isBlank()) {
                        accountRepo.findById(accId).ifPresent(opp::setAccount);
                    } else {
                        opp.setAccount(null);
                    }
                }
                opp.setLastModifiedDate(nowStr);
                opp.markModifiedByCustomApp(userEmail);
                try {
                    Map<String, Object> raw = (opp.getRawData() != null && !opp.getRawData().isBlank())
                            ? objectMapper.readValue(opp.getRawData(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {})
                            : new LinkedHashMap<>();
                    raw.putAll(data);
                    Map<String, Object> normalized = schemaRegistry.normalizeRecord("Opportunity", raw, null);
                    opp.setRawData(objectMapper.writeValueAsString(normalized));
                } catch (Exception ignored) {}
                opportunityRepo.save(opp);
            });
        } else if ("lead".equals(lower)) {
            leadRepo.findById(id).ifPresent(lead -> {
                String fn = data.containsKey("FirstName") ? toNullIfBlank(data.get("FirstName")) : lead.getFirstName();
                String ln = data.containsKey("LastName") ? toNullIfBlank(data.get("LastName")) : lead.getLastName();
                if (data.containsKey("FirstName")) lead.setFirstName(fn);
                if (data.containsKey("LastName")) lead.setLastName(ln);
                lead.setName((Objects.toString(fn, "") + " " + Objects.toString(ln, "")).trim());
                if (data.containsKey("Company")) lead.setCompany(toNullIfBlank(data.get("Company")));
                if (data.containsKey("Email")) lead.setEmail(toNullIfBlank(data.get("Email")));
                if (data.containsKey("Phone")) lead.setPhone(toNullIfBlank(data.get("Phone")));
                if (data.containsKey("Title")) lead.setTitle(toNullIfBlank(data.get("Title")));
                if (data.containsKey("Status")) lead.setStatus(toNullIfBlank(data.get("Status")));
                lead.setLastModifiedDate(nowStr);
                lead.markModifiedByCustomApp(userEmail);
                try {
                    Map<String, Object> raw = (lead.getRawData() != null && !lead.getRawData().isBlank())
                            ? objectMapper.readValue(lead.getRawData(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {})
                            : new LinkedHashMap<>();
                    raw.putAll(data);
                    Map<String, Object> normalized = schemaRegistry.normalizeRecord("Lead", raw, null);
                    lead.setRawData(objectMapper.writeValueAsString(normalized));
                } catch (Exception ignored) {}
                leadRepo.save(lead);
            });
        }
    }

    private void saveLocalWithAudit(String objectName, String id, Map<String, Object> data, String userEmail) {
        String lower = objectName.toLowerCase();
        String nowStr = LocalDateTime.now().toString();

        Map<String, Object> normalized = schemaRegistry.normalizeRecord(objectName, new LinkedHashMap<>(data), null);
        normalized.put("Id", id);
        normalized.put("CreatedDate", nowStr);
        normalized.put("LastModifiedDate", nowStr);

        String rawJson;
        try {
            rawJson = objectMapper.writeValueAsString(normalized);
        } catch (Exception ignored) {
            rawJson = "{}";
        }

        if ("account".equals(lower)) {
            AccountEntity acc = accountRepo.findById(id).orElse(new AccountEntity());
            acc.setId(id);
            acc.setName((String) data.getOrDefault("Name", "New Account"));
            acc.setType((String) data.get("Type"));
            acc.setIndustry((String) data.get("Industry"));
            if (data.containsKey("AnnualRevenue") && data.get("AnnualRevenue") != null && !data.get("AnnualRevenue").toString().isBlank()) {
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
            if (data.containsKey("Amount") && data.get("Amount") != null && !data.get("Amount").toString().isBlank()) {
                try {
                    opp.setAmount(Double.valueOf(data.get("Amount").toString()));
                } catch (Exception ignored) {}
            }
            if (data.containsKey("Probability") && data.get("Probability") != null && !data.get("Probability").toString().isBlank()) {
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

    private String toNullIfBlank(Object obj) {
        if (obj == null) return null;
        String s = String.valueOf(obj).trim();
        return s.isEmpty() ? null : s;
    }

    private String mapSortProperty(String objectName, String prop) {
        if ("Name".equalsIgnoreCase(prop)) return "name";
        if ("CreatedDate".equalsIgnoreCase(prop)) return "createdDate";
        if ("LastModifiedDate".equalsIgnoreCase(prop)) return "lastModifiedDate";
        return "id";
    }

    private Map<String, Object> convertEntityToDetailMap(Object entity) {
        if (entity == null) return null;
        String objectName = "Account";
        Map<String, Object> map = new LinkedHashMap<>();
        String rawData = null;

        if (entity instanceof AccountEntity a) {
            objectName = "Account";
            rawData = a.getRawData();
            map.put("Id", a.getId());
            map.put("id", a.getId());
            map.put("Name", a.getName());
            map.put("name", a.getName());
            map.put("Type", a.getType());
            map.put("Industry", a.getIndustry());
            map.put("Phone", a.getPhone());
            map.put("Website", a.getWebsite());
            map.put("BillingCity", a.getBillingCity());
            map.put("AnnualRevenue", a.getAnnualRevenue());
            map.put("CreatedDate", a.getCreatedDate());
            map.put("LastModifiedDate", a.getLastModifiedDate());
            map.put("SystemModstamp", a.getSystemModstamp());
            populateAuditFields(map, a);
        } else if (entity instanceof ContactEntity c) {
            objectName = "Contact";
            rawData = c.getRawData();
            map.put("Id", c.getId());
            map.put("id", c.getId());
            map.put("Name", c.getName());
            map.put("name", c.getName());
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
            populateAuditFields(map, c);
        } else if (entity instanceof OpportunityEntity o) {
            objectName = "Opportunity";
            rawData = o.getRawData();
            map.put("Id", o.getId());
            map.put("id", o.getId());
            map.put("Name", o.getName());
            map.put("name", o.getName());
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
            populateAuditFields(map, o);
        } else if (entity instanceof LeadEntity l) {
            objectName = "Lead";
            rawData = l.getRawData();
            map.put("Id", l.getId());
            map.put("id", l.getId());
            map.put("Name", l.getName());
            map.put("name", l.getName());
            map.put("FirstName", l.getFirstName());
            map.put("LastName", l.getLastName());
            map.put("Company", l.getCompany());
            map.put("Title", l.getTitle());
            map.put("Email", l.getEmail());
            map.put("Phone", l.getPhone());
            map.put("Status", l.getStatus());
            map.put("CreatedDate", l.getCreatedDate());
            map.put("LastModifiedDate", l.getLastModifiedDate());
            map.put("SystemModstamp", l.getSystemModstamp());
            populateAuditFields(map, l);
        }

        // 1. Unpack all rawData values into the map
        if (rawData != null && !rawData.isBlank()) {
            try {
                Map<String, Object> rawMap = objectMapper.readValue(rawData, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                if (rawMap != null) {
                    for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                        if (!map.containsKey(entry.getKey())) {
                            map.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Normalize with SalesforceSchemaRegistry to ensure EVERY SINGLE FIELD for the object
        // is explicitly present in the map, with null if unpopulated or empty
        Map<String, Object> normalized = schemaRegistry.normalizeRecord(objectName, map, null);

        // 3. Preserve critical aliases and audit fields
        if (map.containsKey("id") && !normalized.containsKey("id")) normalized.put("id", map.get("id"));
        if (map.containsKey("name") && !normalized.containsKey("name")) normalized.put("name", map.get("name"));
        if (map.containsKey("Account_Name")) normalized.put("Account_Name", map.get("Account_Name"));
        if (map.containsKey("account_name")) normalized.put("account_name", map.get("account_name"));
        if (map.containsKey("custom_app_created_by")) normalized.put("custom_app_created_by", map.get("custom_app_created_by"));
        if (map.containsKey("customAppCreatedBy")) normalized.put("customAppCreatedBy", map.get("customAppCreatedBy"));
        if (map.containsKey("custom_app_created_at")) normalized.put("custom_app_created_at", map.get("custom_app_created_at"));
        if (map.containsKey("customAppCreatedAt")) normalized.put("customAppCreatedAt", map.get("customAppCreatedAt"));
        if (map.containsKey("custom_app_modified_by")) normalized.put("custom_app_modified_by", map.get("custom_app_modified_by"));
        if (map.containsKey("customAppModifiedBy")) normalized.put("customAppModifiedBy", map.get("customAppModifiedBy"));
        if (map.containsKey("custom_app_modified_at")) normalized.put("custom_app_modified_at", map.get("custom_app_modified_at"));
        if (map.containsKey("customAppModifiedAt")) normalized.put("customAppModifiedAt", map.get("customAppModifiedAt"));
        if (map.containsKey("is_custom_app_created")) normalized.put("is_custom_app_created", map.get("is_custom_app_created"));
        if (map.containsKey("isCustomAppCreated")) normalized.put("isCustomAppCreated", map.get("isCustomAppCreated"));
        if (map.containsKey("synced_by")) normalized.put("synced_by", map.get("synced_by"));
        if (map.containsKey("syncedBy")) normalized.put("syncedBy", map.get("syncedBy"));
        normalized.put("raw_data", rawData);

        return normalized;
    }

    private void populateAuditFields(Map<String, Object> map, BaseAuditableEntity b) {
        map.put("custom_app_created_by", b.getCustomAppCreatedBy());
        map.put("customAppCreatedBy", b.getCustomAppCreatedBy());
        map.put("custom_app_created_at", b.getCustomAppCreatedAt());
        map.put("customAppCreatedAt", b.getCustomAppCreatedAt());
        map.put("custom_app_modified_by", b.getCustomAppModifiedBy());
        map.put("customAppModifiedBy", b.getCustomAppModifiedBy());
        map.put("custom_app_modified_at", b.getCustomAppModifiedAt());
        map.put("customAppModifiedAt", b.getCustomAppModifiedAt());
        map.put("is_custom_app_created", b.getIsCustomAppCreated());
        map.put("isCustomAppCreated", b.getIsCustomAppCreated());
        map.put("synced_by", b.getSyncedBy());
        map.put("syncedBy", b.getSyncedBy());
    }

    private boolean isReadOnlyUser(Authentication authentication) {
        if (authentication == null) return false;

        if (authentication.getAuthorities() != null) {
            for (GrantedAuthority ga : authentication.getAuthorities()) {
                String auth = ga.getAuthority();
                if ("ROLE_READONLY".equalsIgnoreCase(auth) || "ROLE_READ_ONLY".equalsIgnoreCase(auth)) {
                    return true;
                }
            }
        }

        if (memberRepo != null && authentication.getPrincipal() instanceof UserEntity user) {
            String activeOrgId = OrganizationContext.getCurrentOrganization();
            if (activeOrgId != null && !activeOrgId.isBlank()) {
                Optional<OrganizationMemberEntity> memOpt = memberRepo.findByUserIdAndOrganizationId(user.getId(), activeOrgId);
                if (memOpt.isPresent()) {
                    String role = memOpt.get().getRole();
                    return "READONLY".equalsIgnoreCase(role) || "READ_ONLY".equalsIgnoreCase(role);
                }
            }
        }
        return false;
    }
}
