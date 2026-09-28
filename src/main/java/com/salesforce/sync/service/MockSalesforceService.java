package com.salesforce.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MockSalesforceService {

    private final ObjectMapper objectMapper;
    private final SalesforceSchemaRegistry schemaRegistry;
    private final Map<String, List<Map<String, Object>>> dataStore = new ConcurrentHashMap<>();

    private static final Pattern MODSTAMP_PATTERN = Pattern.compile("SystemModstamp\\s*>\\s*([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_LIKE_PATTERN = Pattern.compile("Name\\s+LIKE\\s+'%([^%']+)%'", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATED_FROM_PATTERN = Pattern.compile("CreatedDate\\s*>=\\s*([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATED_TO_PATTERN = Pattern.compile("CreatedDate\\s*<=\\s*([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOD_FROM_PATTERN = Pattern.compile("LastModifiedDate\\s*>=\\s*([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOD_TO_PATTERN = Pattern.compile("LastModifiedDate\\s*<=\\s*([^\\s]+)", Pattern.CASE_INSENSITIVE);

    public MockSalesforceService(ObjectMapper objectMapper) {
        this(objectMapper, new SalesforceSchemaRegistry());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MockSalesforceService(ObjectMapper objectMapper, SalesforceSchemaRegistry schemaRegistry) {
        this.objectMapper = objectMapper;
        this.schemaRegistry = schemaRegistry != null ? schemaRegistry : new SalesforceSchemaRegistry();
        initMockData();
    }

    public JsonNode describeGlobal() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("encoding", "UTF-8");
        root.put("maxBatchSize", 200);
        ArrayNode sobjects = root.putArray("sobjects");

        addSobjectMeta(sobjects, "Account", "Account", false, "001");
        addSobjectMeta(sobjects, "Contact", "Contact", false, "003");
        addSobjectMeta(sobjects, "Opportunity", "Opportunity", false, "006");
        addSobjectMeta(sobjects, "Lead", "Lead", false, "00Q");

        return root;
    }

    private void addSobjectMeta(ArrayNode sobjects, String name, String label, boolean custom, String keyPrefix) {
        ObjectNode o = sobjects.addObject();
        o.put("name", name);
        o.put("label", label);
        o.put("custom", custom);
        o.put("keyPrefix", keyPrefix);
        o.put("queryable", true);
        o.put("createable", true);
        o.put("updateable", true);
    }

    private void initMockData() {
        // Accounts
        List<Map<String, Object>> accounts = new CopyOnWriteArrayList<>();
        accounts.add(createAccountMap("001mock000000001AAA", "Acme Corporation", "Manufacturing", "San Francisco", "Software", 50000000.0, "(555) 123-4567", "https://acme.example.com", "2026-01-10T08:00:00.000Z"));
        accounts.add(createAccountMap("001mock000000002AAA", "Global Cloud Innovations", "Technology", "Austin", "Enterprise Cloud", 120000000.0, "(555) 234-5678", "https://globalcloud.example.io", "2026-01-12T08:00:00.000Z"));
        accounts.add(createAccountMap("001mock000000003AAA", "Apex Health Systems", "Healthcare", "Boston", "Hospital Care", 85000000.0, "(555) 345-6789", "https://apexhealth.example.org", "2026-01-15T08:00:00.000Z"));
        dataStore.put("Account", accounts);

        // Contacts
        List<Map<String, Object>> contacts = new CopyOnWriteArrayList<>();
        contacts.add(createContactMap("003mock000000001AAA", "Sarah", "Connor", "VP Operations", "sarah@acme.corp", "(555) 123-4568", "Operations", "001mock000000001AAA", "2026-01-16T08:00:00.000Z"));
        contacts.add(createContactMap("003mock000000002AAA", "David", "Kim", "Chief Architect", "david@globalcloud.io", "(555) 234-5679", "Engineering", "001mock000000002AAA", "2026-01-18T08:00:00.000Z"));
        dataStore.put("Contact", contacts);

        // Opportunities
        List<Map<String, Object>> opportunities = new CopyOnWriteArrayList<>();
        opportunities.add(createOpportunityMap("006mock000000001AAA", "Acme - Enterprise Cloud Migration", "Proposal/Price Quote", 125000.0, "2026-11-30", 75.0, "New Business", "001mock000000001AAA", "2026-01-20T08:00:00.000Z"));
        opportunities.add(createOpportunityMap("006mock000000002AAA", "Global Cloud - AI Analytics License", "Closed Won", 280000.0, "2026-10-15", 100.0, "Existing Customer - Upgrade", "001mock000000002AAA", "2026-01-22T08:00:00.000Z"));
        dataStore.put("Opportunity", opportunities);

        // Leads
        List<Map<String, Object>> leads = new CopyOnWriteArrayList<>();
        leads.add(createLeadMap("00Qmock000000001AAA", "Elena", "Rostova", "Horizon Logistics", "elena@horizon.test", "(555) 456-7890", "Director of Procurement", "Open - Not Contacted", "2026-01-25T08:00:00.000Z"));
        leads.add(createLeadMap("00Qmock000000002AAA", "Marcus", "Vance", "Quantum Dynamics", "marcus@quantum.test", "(555) 567-8901", "VP Engineering", "Working - Contacted", "2026-01-28T08:00:00.000Z"));
        dataStore.put("Lead", leads);
    }

    public JsonNode describeObject(String objectName) {
        String canonical = SalesforceSchemaRegistry.canonicalName(objectName);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("name", canonical);
        root.put("label", canonical);
        ArrayNode fields = root.putArray("fields");

        List<SalesforceSchemaRegistry.FieldMeta> standardFields = schemaRegistry.getStandardFields(canonical);
        for (SalesforceSchemaRegistry.FieldMeta f : standardFields) {
            if (f.refObject() != null) {
                addFieldWithRef(fields, f.name(), f.label(), f.type(), f.required(), f.createable(), f.refObject());
            } else {
                addField(fields, f.name(), f.label(), f.type(), f.required(), f.createable());
            }
        }

        return root;
    }

    public JsonNode query(String soql) {
        String canonicalObject = parseTargetObject(soql);
        List<Map<String, Object>> allRecords = dataStore.getOrDefault(canonicalObject, List.of());

        // 1. Check for SystemModstamp filter (incremental sync)
        String modstampFilter = null;
        Matcher modMatcher = MODSTAMP_PATTERN.matcher(soql);
        if (modMatcher.find()) {
            modstampFilter = modMatcher.group(1).trim().replace("'", "");
        }

        // 2. Check for Name LIKE filter
        String nameFilter = null;
        Matcher nameMatcher = NAME_LIKE_PATTERN.matcher(soql);
        if (nameMatcher.find()) {
            nameFilter = nameMatcher.group(1).toLowerCase();
        }

        // 3. Date filters
        String createdFrom = null;
        Matcher cfMatcher = CREATED_FROM_PATTERN.matcher(soql);
        if (cfMatcher.find()) createdFrom = cfMatcher.group(1).trim().replace("'", "");

        String createdTo = null;
        Matcher ctMatcher = CREATED_TO_PATTERN.matcher(soql);
        if (ctMatcher.find()) createdTo = ctMatcher.group(1).trim().replace("'", "");

        String modFrom = null;
        Matcher mfMatcher = MOD_FROM_PATTERN.matcher(soql);
        if (mfMatcher.find()) modFrom = mfMatcher.group(1).trim().replace("'", "");

        String modTo = null;
        Matcher mtMatcher = MOD_TO_PATTERN.matcher(soql);
        if (mtMatcher.find()) modTo = mtMatcher.group(1).trim().replace("'", "");

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> rec : allRecords) {
            if (modstampFilter != null && !modstampFilter.isBlank()) {
                String stamp = (String) rec.get("SystemModstamp");
                if (stamp == null || stamp.compareTo(modstampFilter) <= 0) {
                    continue;
                }
            }
            if (nameFilter != null && !nameFilter.isBlank()) {
                String name = (String) rec.get("Name");
                if (name == null || !name.toLowerCase().contains(nameFilter)) {
                    continue;
                }
            }
            if (createdFrom != null && !createdFrom.isBlank()) {
                String cd = (String) rec.get("CreatedDate");
                if (cd == null || cd.compareTo(createdFrom) < 0) continue;
            }
            if (createdTo != null && !createdTo.isBlank()) {
                String cd = (String) rec.get("CreatedDate");
                if (cd == null || cd.compareTo(createdTo) > 0) continue;
            }
            if (modFrom != null && !modFrom.isBlank()) {
                String md = (String) rec.get("LastModifiedDate");
                if (md == null || md.compareTo(modFrom) < 0) continue;
            }
            if (modTo != null && !modTo.isBlank()) {
                String md = (String) rec.get("LastModifiedDate");
                if (md == null || md.compareTo(modTo) > 0) continue;
            }
            filtered.add(rec);
        }

        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode recordsArray = result.putArray("records");
        for (Map<String, Object> r : filtered) {
            // Ensure all schema fields are present (with null if empty) in the query result
            Map<String, Object> normalized = schemaRegistry.normalizeRecord(canonicalObject, r, null);
            recordsArray.add(objectMapper.valueToTree(normalized));
        }

        result.put("totalSize", filtered.size());
        result.put("done", true);
        return result;
    }

    public Map<String, Object> updateRecord(String objectName, String id, Map<String, Object> fields) {
        String canonicalObject = capitalize(objectName);
        List<Map<String, Object>> list = dataStore.computeIfAbsent(canonicalObject, k -> new CopyOnWriteArrayList<>());
        Map<String, Object> found = null;
        for (Map<String, Object> rec : list) {
            if (id.equals(rec.get("Id"))) {
                found = rec;
                break;
            }
        }
        if (found == null) {
            found = schemaRegistry.normalizeRecord(canonicalObject, new LinkedHashMap<>(), null);
            found.put("Id", id);
            list.add(found);
        }
        if (fields != null) {
            found.putAll(fields);
        }
        String now = Instant.now().toString();
        found.put("LastModifiedDate", now);
        found.put("SystemModstamp", now);
        if (fields != null && (fields.containsKey("FirstName") || fields.containsKey("LastName"))) {
            String fn = Objects.toString(found.getOrDefault("FirstName", ""), "");
            String ln = Objects.toString(found.getOrDefault("LastName", ""), "");
            found.put("Name", (fn + " " + ln).trim());
        }
        return Map.of("success", true, "id", id, "objectName", canonicalObject, "record", found);
    }

    public Map<String, Object> createRecord(String objectName, Map<String, Object> fields) {
        String canonicalObject = capitalize(objectName);
        String prefix = switch (canonicalObject) {
            case "Contact" -> "003";
            case "Opportunity" -> "006";
            case "Lead" -> "00Q";
            default -> "001";
        };

        String newId = prefix + "mock" + UUID.randomUUID().toString().substring(0, 10).replace("-", "");
        String now = Instant.now().toString();

        Map<String, Object> inputFields = fields != null ? new LinkedHashMap<>(fields) : new LinkedHashMap<>();
        Map<String, Object> record = schemaRegistry.normalizeRecord(canonicalObject, inputFields, null);
        record.put("Id", newId);
        record.put("CreatedDate", now);
        record.put("LastModifiedDate", now);
        record.put("SystemModstamp", now);

        // Ensure Name is populated
        if (record.get("Name") == null || String.valueOf(record.get("Name")).isBlank()) {
            if (record.containsKey("FirstName") || record.containsKey("LastName")) {
                String fn = Objects.toString(record.getOrDefault("FirstName", ""), "");
                String ln = Objects.toString(record.getOrDefault("LastName", ""), "");
                String combined = (fn + " " + ln).trim();
                record.put("Name", !combined.isEmpty() ? combined : "New " + canonicalObject);
            } else if (record.containsKey("Company") && record.get("Company") != null) {
                record.put("Name", String.valueOf(record.get("Company")));
            } else {
                record.put("Name", "New " + canonicalObject + " " + newId.substring(newId.length() - 4));
            }
        }

        dataStore.computeIfAbsent(canonicalObject, k -> new CopyOnWriteArrayList<>()).add(record);
        return Map.of("success", true, "id", newId, "objectName", canonicalObject, "record", record);
    }

    public Map<String, Object> simulateNewOrModifiedRecord(String objectName) {
        String canonicalObject = capitalize(objectName);
        List<Map<String, Object>> list = dataStore.computeIfAbsent(canonicalObject, k -> new CopyOnWriteArrayList<>());
        int count = list.size() + 1;
        String now = Instant.now().toString();

        Map<String, Object> raw = new LinkedHashMap<>();
        if ("Account".equalsIgnoreCase(canonicalObject)) {
            raw.put("Id", "001mock" + String.format("%09d", count) + "SIM");
            raw.put("Name", "Simulated Enterprise Corp " + count);
            raw.put("Type", "Customer - Direct");
            raw.put("Industry", "Software");
            raw.put("AnnualRevenue", 15000000.0 + (count * 500000.0));
            raw.put("Phone", "(555) 999-" + String.format("%04d", count));
            raw.put("BillingCity", "Seattle");
            raw.put("Website", "https://simulated" + count + ".example.com");
        } else if ("Contact".equalsIgnoreCase(canonicalObject)) {
            raw.put("Id", "003mock" + String.format("%09d", count) + "SIM");
            raw.put("FirstName", "Simulated");
            raw.put("LastName", "Contact " + count);
            raw.put("Name", "Simulated Contact " + count);
            raw.put("Email", "simulated" + count + "@mock.example.com");
            raw.put("Phone", "(555) 888-" + String.format("%04d", count));
            raw.put("Title", "Director of Cloud Operations");
            raw.put("Department", "Engineering");
            raw.put("AccountId", "001mock000000001AAA");
        } else if ("Opportunity".equalsIgnoreCase(canonicalObject)) {
            raw.put("Id", "006mock" + String.format("%09d", count) + "SIM");
            raw.put("Name", "Simulated Cloud Expansion " + count);
            raw.put("StageName", "Prospecting");
            raw.put("Amount", 250000.0 + (count * 15000.0));
            raw.put("CloseDate", "2026-12-31");
            raw.put("Probability", 40.0);
            raw.put("Type", "New Business");
            raw.put("AccountId", "001mock000000001AAA");
        } else {
            raw.put("Id", "00Qmock" + String.format("%09d", count) + "SIM");
            raw.put("FirstName", "LeadFirst" + count);
            raw.put("LastName", "Simulated" + count);
            raw.put("Name", "LeadFirst" + count + " Simulated" + count);
            raw.put("Company", "Simulated Ventures " + count);
            raw.put("Email", "lead" + count + "@ventures.test");
            raw.put("Phone", "(555) 777-" + String.format("%04d", count));
            raw.put("Title", "Chief Innovation Officer");
            raw.put("Status", "Open - Not Contacted");
        }

        raw.put("CreatedDate", now);
        raw.put("LastModifiedDate", now);
        raw.put("SystemModstamp", now);

        Map<String, Object> newRec = schemaRegistry.normalizeRecord(canonicalObject, raw, null);
        list.add(newRec);
        return newRec;
    }

    private String parseTargetObject(String soql) {
        String lower = soql.toLowerCase();
        if (lower.contains("from account")) return "Account";
        if (lower.contains("from contact")) return "Contact";
        if (lower.contains("from opportunity")) return "Opportunity";
        if (lower.contains("from lead")) return "Lead";
        return "Account";
    }

    private String capitalize(String str) {
        if (str == null || str.isBlank()) return "Account";
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private Map<String, Object> createAccountMap(String id, String name, String industry, String city, String type, Double revenue, String phone, String website, String date) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Id", id);
        m.put("Name", name);
        m.put("Industry", industry);
        m.put("BillingCity", city);
        m.put("Type", type);
        m.put("AnnualRevenue", revenue);
        m.put("Phone", phone);
        m.put("Website", website);
        m.put("CreatedDate", date);
        m.put("LastModifiedDate", date);
        m.put("SystemModstamp", date);
        return schemaRegistry.normalizeRecord("Account", m, null);
    }

    private Map<String, Object> createContactMap(String id, String fn, String ln, String title, String email, String phone, String dept, String accountId, String date) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Id", id);
        m.put("FirstName", fn);
        m.put("LastName", ln);
        m.put("Name", fn + " " + ln);
        m.put("Title", title);
        m.put("Email", email);
        m.put("Phone", phone);
        m.put("Department", dept);
        m.put("AccountId", accountId);
        m.put("CreatedDate", date);
        m.put("LastModifiedDate", date);
        m.put("SystemModstamp", date);
        return schemaRegistry.normalizeRecord("Contact", m, null);
    }

    private Map<String, Object> createOpportunityMap(String id, String name, String stage, Double amount, String closeDate, Double prob, String type, String accountId, String date) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Id", id);
        m.put("Name", name);
        m.put("StageName", stage);
        m.put("Amount", amount);
        m.put("CloseDate", closeDate);
        m.put("Probability", prob);
        m.put("Type", type);
        m.put("AccountId", accountId);
        m.put("CreatedDate", date);
        m.put("LastModifiedDate", date);
        m.put("SystemModstamp", date);
        return schemaRegistry.normalizeRecord("Opportunity", m, null);
    }

    private Map<String, Object> createLeadMap(String id, String fn, String ln, String company, String email, String phone, String title, String status, String date) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Id", id);
        m.put("FirstName", fn);
        m.put("LastName", ln);
        m.put("Name", fn + " " + ln);
        m.put("Company", company);
        m.put("Email", email);
        m.put("Phone", phone);
        m.put("Title", title);
        m.put("Status", status);
        m.put("CreatedDate", date);
        m.put("LastModifiedDate", date);
        m.put("SystemModstamp", date);
        return schemaRegistry.normalizeRecord("Lead", m, null);
    }

    private void addField(ArrayNode fields, String name, String label, String type, boolean required, boolean creatable) {
        ObjectNode f = fields.addObject();
        f.put("name", name);
        f.put("label", label);
        f.put("type", type);
        f.put("nillable", !required);
        f.put("createable", creatable);
        f.put("updateable", creatable);
        f.put("queryable", true);
        f.put("deprecatedAndHidden", false);
    }

    private void addFieldWithRef(ArrayNode fields, String name, String label, String type, boolean required, boolean creatable, String refObject) {
        ObjectNode f = fields.addObject();
        f.put("name", name);
        f.put("label", label);
        f.put("type", type);
        f.put("nillable", !required);
        f.put("createable", creatable);
        f.put("updateable", creatable);
        f.put("queryable", true);
        f.put("deprecatedAndHidden", false);
        ArrayNode refs = f.putArray("referenceTo");
        refs.add(refObject);
    }
}
