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

    private final Map<String, Object> currentJob = Collections.synchronizedMap(new HashMap<>());

    public SalesforceSyncService(SalesforceClientService sfClient,
                                 AccountRepository accountRepo,
                                 ContactRepository contactRepo,
                                 OpportunityRepository opportunityRepo,
                                 LeadRepository leadRepo,
                                 SyncHistoryRepository historyRepo,
                                 SyncStateRepository stateRepo,
                                 ObjectMapper objectMapper) {
        this.sfClient = sfClient;
        this.accountRepo = accountRepo;
        this.contactRepo = contactRepo;
        this.opportunityRepo = opportunityRepo;
        this.leadRepo = leadRepo;
        this.historyRepo = historyRepo;
        this.stateRepo = stateRepo;
        this.objectMapper = objectMapper;
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

        executeAsyncSync(objects, mode, filters != null ? filters : Map.of(), effectiveUser);

        return Map.of("jobId", jobId, "status", "started", "objects", objects, "mode", mode, "userEmail", effectiveUser);
    }

    @Async
    public void executeAsyncSync(List<String> objects, String mode, Map<String, Object> filters) {
        executeAsyncSync(objects, mode, filters, "cli@app.local");
    }

    @Async
    public void executeAsyncSync(List<String> objects, String mode, Map<String, Object> filters, String userEmail) {
        int totalObjects = objects.size();
        int completed = 0;

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) currentJob.get("details");
        if (details == null) {
            details = new ConcurrentHashMap<>();
            currentJob.put("details", details);
        }

        try {
            for (String objectName : objects) {
                currentJob.put("currentObject", objectName);
                currentJob.put("progressPercent", (int) Math.round(((double) completed / totalObjects) * 100));
                appendLog("Starting sync for object \"" + objectName + "\" on behalf of user " + userEmail + "...", "info");

                SyncHistoryEntity history = new SyncHistoryEntity();
                history.setObjectName(objectName);
                history.setSyncMode(mode);
                history.setStatus("RUNNING");
                history.setUserEmail(userEmail);
                history.setStartTime(LocalDateTime.now());
                history = historyRepo.save(history);

                long startTime = System.currentTimeMillis();
                try {
                    int count = syncSingleObject(objectName, mode, filters, userEmail);
                    long duration = System.currentTimeMillis() - startTime;

                    history.setStatus("SUCCESS");
                    history.setRecordsFetched(count);
                    history.setRecordsUpserted(count);
                    history.setEndTime(LocalDateTime.now());
                    history.setDurationMs(duration);
                    historyRepo.save(history);

                    details.put(objectName, Map.of(
                            "status", "success",
                            "fetched", count,
                            "upserted", count,
                            "durationMs", duration
                    ));

                    int total = (int) currentJob.getOrDefault("totalRecordsSynced", 0) + count;
                    currentJob.put("totalRecordsSynced", total);
                    appendLog("Completed \"" + objectName + "\": " + count + " upserted in " + duration + "ms", "success");
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - startTime;
                    history.setStatus("ERROR");
                    history.setErrorMessage(e.getMessage());
                    history.setEndTime(LocalDateTime.now());
                    history.setDurationMs(duration);
                    historyRepo.save(history);

                    details.put(objectName, Map.of(
                            "status", "error",
                            "fetched", 0,
                            "upserted", 0,
                            "durationMs", duration,
                            "error", e.getMessage() != null ? e.getMessage() : "Sync failed"
                    ));

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

        StringBuilder soql = new StringBuilder("SELECT Id, Name, SystemModstamp, LastModifiedDate, CreatedDate");

        if ("Account".equalsIgnoreCase(objectName)) {
            soql.append(", Type, Industry, AnnualRevenue, Phone, Website, BillingCity");
        } else if ("Contact".equalsIgnoreCase(objectName)) {
            soql.append(", AccountId, FirstName, LastName, Email, Phone, Title, Department");
        } else if ("Opportunity".equalsIgnoreCase(objectName)) {
            soql.append(", AccountId, StageName, Amount, CloseDate, Probability, Type");
        } else if ("Lead".equalsIgnoreCase(objectName)) {
            soql.append(", FirstName, LastName, Company, Email, Phone, Title, Status");
        }

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

                upsertEntity(objectName, id, r, effectiveUser);
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

    private void upsertEntity(String objectName, String id, JsonNode r, String userEmail) {
        String rawData = r.toString();
        String syncedAt = LocalDateTime.now().toString();

        if ("Account".equalsIgnoreCase(objectName)) {
            AccountEntity acc = accountRepo.findById(id).orElse(new AccountEntity());
            acc.setId(id);
            acc.setName(r.path("Name").asText(null));
            acc.setType(r.path("Type").asText(null));
            acc.setIndustry(r.path("Industry").asText(null));
            if (r.hasNonNull("AnnualRevenue")) acc.setAnnualRevenue(r.path("AnnualRevenue").asDouble());
            acc.setPhone(r.path("Phone").asText(null));
            acc.setWebsite(r.path("Website").asText(null));
            acc.setBillingCity(r.path("BillingCity").asText(null));
            acc.setCreatedDate(r.path("CreatedDate").asText(null));
            acc.setLastModifiedDate(r.path("LastModifiedDate").asText(null));
            acc.setSystemModstamp(r.path("SystemModstamp").asText(null));
            acc.setRawData(rawData);
            acc.setSyncedAt(syncedAt);
            // CRITICAL: Attribute sync to authenticated user while preserving local custom app creation
            acc.addSyncedBy(userEmail);
            accountRepo.save(acc);
        } else if ("Contact".equalsIgnoreCase(objectName)) {
            ContactEntity con = contactRepo.findById(id).orElse(new ContactEntity());
            con.setId(id);
            con.setName(r.path("Name").asText(null));
            con.setFirstName(r.path("FirstName").asText(null));
            con.setLastName(r.path("LastName").asText(null));
            con.setEmail(r.path("Email").asText(null));
            con.setPhone(r.path("Phone").asText(null));
            con.setTitle(r.path("Title").asText(null));
            con.setDepartment(r.path("Department").asText(null));
            con.setCreatedDate(r.path("CreatedDate").asText(null));
            con.setLastModifiedDate(r.path("LastModifiedDate").asText(null));
            con.setSystemModstamp(r.path("SystemModstamp").asText(null));
            con.setRawData(rawData);
            con.setSyncedAt(syncedAt);

            String accId = r.path("AccountId").asText(null);
            if (accId != null && !accId.isBlank()) {
                accountRepo.findById(accId).ifPresent(con::setAccount);
            }
            // CRITICAL: Attribute sync to authenticated user while preserving local custom app creation
            con.addSyncedBy(userEmail);
            contactRepo.save(con);
        } else if ("Opportunity".equalsIgnoreCase(objectName)) {
            OpportunityEntity opp = opportunityRepo.findById(id).orElse(new OpportunityEntity());
            opp.setId(id);
            opp.setName(r.path("Name").asText(null));
            opp.setStageName(r.path("StageName").asText(null));
            if (r.hasNonNull("Amount")) opp.setAmount(r.path("Amount").asDouble());
            opp.setCloseDate(r.path("CloseDate").asText(null));
            if (r.hasNonNull("Probability")) opp.setProbability(r.path("Probability").asDouble());
            opp.setType(r.path("Type").asText(null));
            opp.setCreatedDate(r.path("CreatedDate").asText(null));
            opp.setLastModifiedDate(r.path("LastModifiedDate").asText(null));
            opp.setSystemModstamp(r.path("SystemModstamp").asText(null));
            opp.setRawData(rawData);
            opp.setSyncedAt(syncedAt);

            String accId = r.path("AccountId").asText(null);
            if (accId != null && !accId.isBlank()) {
                accountRepo.findById(accId).ifPresent(opp::setAccount);
            }
            // CRITICAL: Attribute sync to authenticated user while preserving local custom app creation
            opp.addSyncedBy(userEmail);
            opportunityRepo.save(opp);
        } else if ("Lead".equalsIgnoreCase(objectName)) {
            LeadEntity lead = leadRepo.findById(id).orElse(new LeadEntity());
            lead.setId(id);
            lead.setName(r.path("Name").asText(null));
            lead.setFirstName(r.path("FirstName").asText(null));
            lead.setLastName(r.path("LastName").asText(null));
            lead.setCompany(r.path("Company").asText(null));
            lead.setEmail(r.path("Email").asText(null));
            lead.setPhone(r.path("Phone").asText(null));
            lead.setTitle(r.path("Title").asText(null));
            lead.setStatus(r.path("Status").asText(null));
            lead.setCreatedDate(r.path("CreatedDate").asText(null));
            lead.setLastModifiedDate(r.path("LastModifiedDate").asText(null));
            lead.setSystemModstamp(r.path("SystemModstamp").asText(null));
            lead.setRawData(rawData);
            lead.setSyncedAt(syncedAt);
            // CRITICAL: Attribute sync to authenticated user while preserving local custom app creation
            lead.addSyncedBy(userEmail);
            leadRepo.save(lead);
        }
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
        log.info("[SyncService] [{}] {}", type.toUpperCase(), message);
    }

    public Map<String, Object> getStatus() {
        return new HashMap<>(currentJob);
    }

    private void resetCurrentJob() {
        currentJob.put("id", "");
        currentJob.put("status", "idle");
        currentJob.put("progressPercent", 0);
        currentJob.put("currentObject", "");
        currentJob.put("totalRecordsSynced", 0);
        currentJob.put("details", new ConcurrentHashMap<String, Object>());
        currentJob.put("logs", new java.util.concurrent.CopyOnWriteArrayList<Map<String, String>>());
    }
}
