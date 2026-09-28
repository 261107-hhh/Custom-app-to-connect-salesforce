package com.salesforce.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.sync.model.entity.*;
import com.salesforce.sync.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SalesforceSyncService {

    private static final Logger log = LoggerFactory.getLogger(SalesforceSyncService.class);

    private final SalesforceClientService sfClient;
    private final AccountRepository accountRepo;
    private final ContactRepository contactRepo;
    private final OpportunityRepository opportunityRepo;
    private final LeadRepository leadRepo;
    private final SyncHistoryRepository historyRepo;
    private final SyncStateRepository stateRepo;
    private final ObjectMapper objectMapper;
    private final SalesforceSchemaRegistry schemaRegistry;

    private final Map<String, Object> currentJob = Collections.synchronizedMap(new HashMap<>());

    public SalesforceSyncService(SalesforceClientService sfClient,
                                 AccountRepository accountRepo,
                                 ContactRepository contactRepo,
                                 OpportunityRepository opportunityRepo,
                                 LeadRepository leadRepo,
                                 SyncHistoryRepository historyRepo,
                                 SyncStateRepository stateRepo,
                                 ObjectMapper objectMapper) {
        this(sfClient, accountRepo, contactRepo, opportunityRepo, leadRepo, historyRepo, stateRepo, objectMapper, new SalesforceSchemaRegistry());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SalesforceSyncService(SalesforceClientService sfClient,
                                 AccountRepository accountRepo,
                                 ContactRepository contactRepo,
                                 OpportunityRepository opportunityRepo,
                                 LeadRepository leadRepo,
                                 SyncHistoryRepository historyRepo,
                                 SyncStateRepository stateRepo,
                                 ObjectMapper objectMapper,
                                 SalesforceSchemaRegistry schemaRegistry) {
        this.sfClient = sfClient;
        this.accountRepo = accountRepo;
        this.contactRepo = contactRepo;
        this.opportunityRepo = opportunityRepo;
        this.leadRepo = leadRepo;
        this.historyRepo = historyRepo;
        this.stateRepo = stateRepo;
        this.objectMapper = objectMapper;
        this.schemaRegistry = schemaRegistry != null ? schemaRegistry : new SalesforceSchemaRegistry();
        resetCurrentJob();
    }

    public synchronized Map<String, Object> runSync(List<String> objects, String mode, Map<String, Object> filters) {
        return runSync(objects, mode, filters, "cli@app.local");
    }

    public synchronized Map<String, Object> runSync(List<String> objects, String mode, Map<String, Object> filters, String userEmail) {
        if ("running".equals(currentJob.get("status"))) {
            throw new IllegalStateException("A sync job is already in progress.");
        }

        String effectiveUser = (userEmail != null && !userEmail.isBlank()) ? userEmail : "cli@app.local";
        String jobId = "job-" + System.currentTimeMillis();
        currentJob.put("id", jobId);
        currentJob.put("status", "running");
        currentJob.put("progressPercent", 0);
        currentJob.put("currentObject", null);
        currentJob.put("objects", objects);
        currentJob.put("mode", mode);
        currentJob.put("userEmail", effectiveUser);
        currentJob.put("totalRecordsSynced", 0);
        currentJob.put("details", new ConcurrentHashMap<String, Object>());
        currentJob.put("startedAt", LocalDateTime.now().toString());
        currentJob.put("finishedAt", null);
        currentJob.put("error", null);
        currentJob.put("logs", new java.util.concurrent.CopyOnWriteArrayList<Map<String, String>>());

        appendLog("Sync process started for objects: " + String.join(", ", objects) + " [User: " + effectiveUser + "]", "info");

        executeSyncAsync(objects, mode, filters, effectiveUser);

        return new HashMap<>(currentJob);
    }

    public Map<String, Object> getStatus() {
        return new HashMap<>(currentJob);
    }

    private void resetCurrentJob() {
        currentJob.put("id", null);
        currentJob.put("status", "idle");
        currentJob.put("progressPercent", 0);
        currentJob.put("currentObject", null);
        currentJob.put("objects", List.of());
        currentJob.put("mode", null);
        currentJob.put("totalRecordsSynced", 0);
        currentJob.put("details", new ConcurrentHashMap<String, Object>());
        currentJob.put("startedAt", null);
        currentJob.put("finishedAt", null);
        currentJob.put("error", null);
        currentJob.put("logs", new java.util.concurrent.CopyOnWriteArrayList<Map<String, String>>());
    }

    @Async
    public void executeSyncAsync(List<String> objects, String mode, Map<String, Object> filters, String userEmail) {
        int totalObjects = objects.size();
        int completed = 0;

        try {
            for (String objectName : objects) {
                currentJob.put("currentObject", objectName);
                appendLog("Starting sync for object: " + objectName, "info");

                LocalDateTime startObjTime = LocalDateTime.now();
                int recordsSynced = 0;
                String status = "SUCCESS";
                String errMsg = null;

                try {
                    recordsSynced = syncSingleObject(objectName, mode, filters, userEmail);
                    appendLog("Successfully synced " + recordsSynced + " records for object: " + objectName, "success");

                    @SuppressWarnings("unchecked")
                    Map<String, Object> details = (Map<String, Object>) currentJob.get("details");
                    details.put(objectName, Map.of(
                            "recordsSynced", recordsSynced,
                            "status", "SUCCESS",
                            "finishedAt", LocalDateTime.now().toString()
                    ));

                    int total = (int) currentJob.getOrDefault("totalRecordsSynced", 0) + recordsSynced;
                    currentJob.put("totalRecordsSynced", total);

                    SyncHistoryEntity history = new SyncHistoryEntity();
                    history.setObjectName(objectName);
                    history.setSyncMode(mode);
                    history.setRecordsFetched(recordsSynced);
                    history.setRecordsUpserted(recordsSynced);
                    history.setStatus(status);
                    history.setStartTime(startObjTime);
                    LocalDateTime finishTime = LocalDateTime.now();
                    history.setEndTime(finishTime);
                    history.setDurationMs(java.time.Duration.between(startObjTime, finishTime).toMillis());
                    history.setUserEmail(userEmail);
                    historyRepo.save(history);

                } catch (Exception e) {
                    status = "FAILED";
                    errMsg = e.getMessage();
                    log.error("Failed to sync object " + objectName, e);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> details = (Map<String, Object>) currentJob.get("details");
                    details.put(objectName, Map.of(
                            "recordsSynced", 0,
                            "status", "FAILED",
                            "error", e.getMessage() != null ? e.getMessage() : "Unknown error",
                            "finishedAt", LocalDateTime.now().toString()
                    ));

                    SyncHistoryEntity history = new SyncHistoryEntity();
                    history.setObjectName(objectName);
                    history.setSyncMode(mode);
                    history.setRecordsFetched(0);
                    history.setRecordsUpserted(0);
                    history.setStatus(status);
                    history.setErrorMessage(errMsg);
                    history.setStartTime(startObjTime);
                    LocalDateTime finishTime = LocalDateTime.now();
                    history.setEndTime(finishTime);
                    history.setDurationMs(java.time.Duration.between(startObjTime, finishTime).toMillis());
                    history.setUserEmail(userEmail);
                    historyRepo.save(history);

                    appendLog("Error syncing \"" + objectName + "\": " + e.getMessage(), "error");
                }

                completed++;
                currentJob.put("progressPercent", (int) Math.round(((double) completed / totalObjects) * 100));
            }

            currentJob.put("status", "completed");
            currentJob.put("currentObject", null);
            currentJob.put("finishedAt", LocalDateTime.now().toString());
            appendLog("All sync tasks finished. Total records synced: " + currentJob.get("totalRecordsSynced"), "success");
        } catch (Exception e) {
            currentJob.put("status", "failed");
            currentJob.put("error", e.getMessage());
            appendLog("Sync process failed: " + e.getMessage(), "error");
        }
    }

    @Transactional
    public int syncSingleObject(String objectName, String mode, Map<String, Object> filters) throws Exception {
        return syncSingleObject(objectName, mode, filters, "cli@app.local");
    }

    @Transactional
    public int syncSingleObject(String objectName, String mode, Map<String, Object> filters, String userEmail) throws Exception {
        String effectiveUser = (userEmail != null && !userEmail.isBlank()) ? userEmail : "cli@app.local";
        String lastModstamp = null;
        if ("incremental".equalsIgnoreCase(mode)) {
            Optional<SyncStateEntity> stateOpt = stateRepo.findById(objectName);
            if (stateOpt.isPresent()) {
                lastModstamp = stateOpt.get().getLastSyncTimestamp();
            }
        }

        // Introspect object describe to dynamically query ALL fields from Salesforce
        JsonNode describeNode = null;
        try {
            describeNode = sfClient.describeObject(objectName);
        } catch (Exception e) {
            log.warn("[SyncService] Could not describe {}: {}", objectName, e.getMessage());
        }

        List<String> queryFields = schemaRegistry.extractQueryableFields(objectName, describeNode);
        if (queryFields.isEmpty()) {
            queryFields = List.of("Id", "Name", "SystemModstamp", "LastModifiedDate", "CreatedDate");
        }

        StringBuilder soql = new StringBuilder("SELECT ");
        soql.append(String.join(", ", queryFields));
        soql.append(" FROM ").append(objectName);

        List<String> whereClauses = new ArrayList<>();
        if (lastModstamp != null && !lastModstamp.isBlank()) {
            whereClauses.add("SystemModstamp > " + lastModstamp);
        }

        // Handle user filters
        String nameFilter = null;
        if (filters.containsKey("name") && filters.get("name") != null && !filters.get("name").toString().isBlank()) {
            nameFilter = filters.get("name").toString().trim();
        } else if (filters.containsKey("nameContains") && filters.get("nameContains") != null && !filters.get("nameContains").toString().isBlank()) {
            nameFilter = filters.get("nameContains").toString().trim();
        }
        if (nameFilter != null) {
            whereClauses.add("Name LIKE '%" + nameFilter.replace("'", "\\'") + "%'");
            appendLog("Filter applied: Name LIKE '%" + nameFilter + "%'", "info");
        }

        if (filters.containsKey("createdFrom") && filters.get("createdFrom") != null && !filters.get("createdFrom").toString().isBlank()) {
            String val = filters.get("createdFrom").toString().trim();
            String isoDate = val.contains("T") ? val : val + "T00:00:00.000Z";
            whereClauses.add("CreatedDate >= " + isoDate);
            appendLog("Filter applied: CreatedDate >= " + isoDate, "info");
        }
        if (filters.containsKey("createdTo") && filters.get("createdTo") != null && !filters.get("createdTo").toString().isBlank()) {
            String val = filters.get("createdTo").toString().trim();
            String isoDate = val.contains("T") ? val : val + "T23:59:59.999Z";
            whereClauses.add("CreatedDate <= " + isoDate);
            appendLog("Filter applied: CreatedDate <= " + isoDate, "info");
        }
        if (filters.containsKey("modifiedFrom") && filters.get("modifiedFrom") != null && !filters.get("modifiedFrom").toString().isBlank()) {
            String val = filters.get("modifiedFrom").toString().trim();
            String isoDate = val.contains("T") ? val : val + "T00:00:00.000Z";
            whereClauses.add("LastModifiedDate >= " + isoDate);
            appendLog("Filter applied: LastModifiedDate >= " + isoDate, "info");
        }
        if (filters.containsKey("modifiedTo") && filters.get("modifiedTo") != null && !filters.get("modifiedTo").toString().isBlank()) {
            String val = filters.get("modifiedTo").toString().trim();
            String isoDate = val.contains("T") ? val : val + "T23:59:59.999Z";
            whereClauses.add("LastModifiedDate <= " + isoDate);
            appendLog("Filter applied: LastModifiedDate <= " + isoDate, "info");
        }

        if (!whereClauses.isEmpty()) {
            soql.append(" WHERE ").append(String.join(" AND ", whereClauses));
        }

        soql.append(" ORDER BY SystemModstamp ASC LIMIT 2000");

        appendLog("Executing SOQL: " + soql, "info");
        JsonNode queryRes = sfClient.query(soql.toString());

        JsonNode recordsNode = queryRes.path("records");
        int count = 0;
        String latestStamp = null;

        if (recordsNode.isArray()) {
            for (JsonNode r : recordsNode) {
                String id = r.path("Id").asText();
                String sysMod = r.path("SystemModstamp").asText(null);
                if (sysMod != null) latestStamp = sysMod;

                upsertEntity(objectName, id, r, effectiveUser, describeNode);
                count++;
            }
        }

        // Update Sync State
        SyncStateEntity state = stateRepo.findById(objectName).orElse(new SyncStateEntity());
        state.setObjectName(objectName);
        if (latestStamp != null) {
            state.setLastSyncTimestamp(latestStamp);
        }
        state.setLastSyncMode(mode);
        state.setTotalRecords(getObjectCountByUser(objectName, effectiveUser));
        state.setLastStatus("SUCCESS");
        state.setUpdatedAt(LocalDateTime.now());
        stateRepo.save(state);

        return count;
    }

    private void upsertEntity(String objectName, String id, JsonNode r, String userEmail, JsonNode describeNode) {
        Map<String, Object> rawMap = objectMapper.convertValue(r, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        Map<String, Object> normalized = schemaRegistry.normalizeRecord(objectName, rawMap, describeNode);
        String rawData;
        try {
            rawData = objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            rawData = r.toString();
        }
        String syncedAt = LocalDateTime.now().toString();

        if ("Account".equalsIgnoreCase(objectName)) {
            AccountEntity acc = accountRepo.findById(id).orElse(new AccountEntity());
            acc.setId(id);
            acc.setName(getStringValue(normalized, "Name"));
            acc.setType(getStringValue(normalized, "Type"));
            acc.setIndustry(getStringValue(normalized, "Industry"));
            if (normalized.get("AnnualRevenue") != null && !normalized.get("AnnualRevenue").toString().isBlank()) {
                try {
                    acc.setAnnualRevenue(Double.valueOf(normalized.get("AnnualRevenue").toString()));
                } catch (Exception ignored) {}
            }
            acc.setPhone(getStringValue(normalized, "Phone"));
            acc.setWebsite(getStringValue(normalized, "Website"));
            acc.setBillingCity(getStringValue(normalized, "BillingCity"));
            acc.setCreatedDate(getStringValue(normalized, "CreatedDate"));
            acc.setLastModifiedDate(getStringValue(normalized, "LastModifiedDate"));
            acc.setSystemModstamp(getStringValue(normalized, "SystemModstamp"));
            acc.setRawData(rawData);
            acc.setSyncedAt(syncedAt);
            // CRITICAL: Attribute sync to authenticated user while preserving local custom app creation
            acc.addSyncedBy(userEmail);
            accountRepo.save(acc);
        } else if ("Contact".equalsIgnoreCase(objectName)) {
            ContactEntity con = contactRepo.findById(id).orElse(new ContactEntity());
            con.setId(id);
            con.setName(getStringValue(normalized, "Name"));
            con.setFirstName(getStringValue(normalized, "FirstName"));
            con.setLastName(getStringValue(normalized, "LastName"));
            con.setEmail(getStringValue(normalized, "Email"));
            con.setPhone(getStringValue(normalized, "Phone"));
            con.setTitle(getStringValue(normalized, "Title"));
            con.setDepartment(getStringValue(normalized, "Department"));
            con.setCreatedDate(getStringValue(normalized, "CreatedDate"));
            con.setLastModifiedDate(getStringValue(normalized, "LastModifiedDate"));
            con.setSystemModstamp(getStringValue(normalized, "SystemModstamp"));
            con.setRawData(rawData);
            con.setSyncedAt(syncedAt);

            String accId = getStringValue(normalized, "AccountId");
            if (accId != null && !accId.isBlank()) {
                accountRepo.findById(accId).ifPresent(con::setAccount);
            }
            con.addSyncedBy(userEmail);
            contactRepo.save(con);
        } else if ("Opportunity".equalsIgnoreCase(objectName)) {
            OpportunityEntity opp = opportunityRepo.findById(id).orElse(new OpportunityEntity());
            opp.setId(id);
            opp.setName(getStringValue(normalized, "Name"));
            opp.setStageName(getStringValue(normalized, "StageName"));
            if (normalized.get("Amount") != null && !normalized.get("Amount").toString().isBlank()) {
                try {
                    opp.setAmount(Double.valueOf(normalized.get("Amount").toString()));
                } catch (Exception ignored) {}
            }
            opp.setCloseDate(getStringValue(normalized, "CloseDate"));
            if (normalized.get("Probability") != null && !normalized.get("Probability").toString().isBlank()) {
                try {
                    opp.setProbability(Double.valueOf(normalized.get("Probability").toString()));
                } catch (Exception ignored) {}
            }
            opp.setType(getStringValue(normalized, "Type"));
            opp.setCreatedDate(getStringValue(normalized, "CreatedDate"));
            opp.setLastModifiedDate(getStringValue(normalized, "LastModifiedDate"));
            opp.setSystemModstamp(getStringValue(normalized, "SystemModstamp"));
            opp.setRawData(rawData);
            opp.setSyncedAt(syncedAt);

            String accId = getStringValue(normalized, "AccountId");
            if (accId != null && !accId.isBlank()) {
                accountRepo.findById(accId).ifPresent(opp::setAccount);
            }
            opp.addSyncedBy(userEmail);
            opportunityRepo.save(opp);
        } else if ("Lead".equalsIgnoreCase(objectName)) {
            LeadEntity lead = leadRepo.findById(id).orElse(new LeadEntity());
            lead.setId(id);
            lead.setName(getStringValue(normalized, "Name"));
            lead.setFirstName(getStringValue(normalized, "FirstName"));
            lead.setLastName(getStringValue(normalized, "LastName"));
            lead.setCompany(getStringValue(normalized, "Company"));
            lead.setEmail(getStringValue(normalized, "Email"));
            lead.setPhone(getStringValue(normalized, "Phone"));
            lead.setTitle(getStringValue(normalized, "Title"));
            lead.setStatus(getStringValue(normalized, "Status"));
            lead.setCreatedDate(getStringValue(normalized, "CreatedDate"));
            lead.setLastModifiedDate(getStringValue(normalized, "LastModifiedDate"));
            lead.setSystemModstamp(getStringValue(normalized, "SystemModstamp"));
            lead.setRawData(rawData);
            lead.setSyncedAt(syncedAt);
            lead.addSyncedBy(userEmail);
            leadRepo.save(lead);
        }
    }

    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) return null;
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }

    public long getObjectCountByUser(String objectName, String userEmail) {
        if (userEmail == null || userEmail.isBlank()) return 0L;
        if ("Account".equalsIgnoreCase(objectName)) return accountRepo.countByUser(userEmail);
        if ("Contact".equalsIgnoreCase(objectName)) return contactRepo.countByUser(userEmail);
        if ("Opportunity".equalsIgnoreCase(objectName)) return opportunityRepo.countByUser(userEmail);
        if ("Lead".equalsIgnoreCase(objectName)) return leadRepo.countByUser(userEmail);
        return 0L;
    }

    private long getObjectCount(String objectName) {
        if ("Account".equalsIgnoreCase(objectName)) return accountRepo.count();
        if ("Contact".equalsIgnoreCase(objectName)) return contactRepo.count();
        if ("Opportunity".equalsIgnoreCase(objectName)) return opportunityRepo.count();
        if ("Lead".equalsIgnoreCase(objectName)) return leadRepo.count();
        return 0L;
    }

    private static final java.time.format.DateTimeFormatter TIME_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");

    private void appendLog(String message, String type) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> logs = (List<Map<String, String>>) currentJob.get("logs");
        if (logs != null) {
            String timeStr = java.time.LocalTime.now().format(TIME_FORMATTER);
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("timestamp", timeStr);
            entry.put("message", message);
            entry.put("type", type);
            entry.put("level", type);
            logs.add(entry);
            if (logs.size() > 250) {
                logs.remove(0);
            }
        }
    }
}
