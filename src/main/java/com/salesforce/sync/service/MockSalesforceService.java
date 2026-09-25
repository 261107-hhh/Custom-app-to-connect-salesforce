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
    private final Map<String, List<Map<String, Object>>> dataStore = new ConcurrentHashMap<>();

    private static final Pattern MODSTAMP_PATTERN = Pattern.compile("SystemModstamp\\s*>\\s*([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_LIKE_PATTERN = Pattern.compile("Name\\s+LIKE\\s+'%([^%']+)%'", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATED_FROM_PATTERN = Pattern.compile("CreatedDate\\s*>=\\s*([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATED_TO_PATTERN = Pattern.compile("CreatedDate\\s*<=\\s*([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOD_FROM_PATTERN = Pattern.compile("LastModifiedDate\\s*>=\\s*([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOD_TO_PATTERN = Pattern.compile("LastModifiedDate\\s*<=\\s*([^\\s]+)", Pattern.CASE_INSENSITIVE);

    public MockSalesforceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
        ObjectNode root = objectMapper.createObjectNode();
        root.put("name", objectName);
        root.put("label", objectName);
        ArrayNode fields = root.putArray("fields");

        addField(fields, "Id", "Record ID", "id", false, true);
        addField(fields, "Name", "Name", "string", true, true);
        addField(fields, "CreatedDate", "Created Date", "datetime", false, false);
        addField(fields, "LastModifiedDate", "Last Modified Date", "datetime", false, false);
        addField(fields, "SystemModstamp", "System Modstamp", "datetime", false, false);

        if ("Account".equalsIgnoreCase(objectName)) {
            addField(fields, "Type", "Account Type", "picklist", false, true);
            addField(fields, "Industry", "Industry", "picklist", false, true);
            addField(fields, "AnnualRevenue", "Annual Revenue", "currency", false, true);
            addField(fields, "Phone", "Phone", "phone", false, true);
            addField(fields, "Website", "Website", "url", false, true);
            addField(fields, "BillingCity", "Billing City", "string", false, true);
        } else if ("Contact".equalsIgnoreCase(objectName)) {
            addFieldWithRef(fields, "AccountId", "Account ID", "reference", false, true, "Account");
            addField(fields, "FirstName", "First Name", "string", false, true);
            addField(fields, "LastName", "Last Name", "string", true, true);
            addField(fields, "Email", "Email", "email", false, true);
            addField(fields, "Phone", "Phone", "phone", false, true);
            addField(fields, "Title", "Title", "string", false, true);
            addField(fields, "Department", "Department", "string", false, true);
        } else if ("Opportunity".equalsIgnoreCase(objectName)) {
            addFieldWithRef(fields, "AccountId", "Account ID", "reference", false, true, "Account");
            addField(fields, "StageName", "Stage", "picklist", true, true);
            addField(fields, "Amount", "Amount", "currency", false, true);
            addField(fields, "CloseDate", "Close Date", "date", true, true);
            addField(fields, "Probability", "Probability (%)", "percent", false, true);
            addField(fields, "Type", "Opportunity Type", "picklist", false, true);
        } else if ("Lead".equalsIgnoreCase(objectName)) {
            addField(fields, "FirstName", "First Name", "string", false, true);
            addField(fields, "LastName", "Last Name", "string", true, true);
            addField(fields, "Company", "Company", "string", true, true);
            addField(fields, "Email", "Email", "email", false, true);
            addField(fields, "Phone", "Phone", "phone", false, true);
            addField(fields, "Title", "Title", "string", false, true);
            addField(fields, "Status", "Status", "picklist", true, true);
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
            recordsArray.add(objectMapper.valueToTree(r));
        }

        result.put("totalSize", filtered.size());
        result.put("done", true);
        return result;
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

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("Id", newId);
        record.put("CreatedDate", now);
        record.put("LastModifiedDate", now);
        record.put("SystemModstamp", now);

        if (fields != null) {
            record.putAll(fields);
        }

        // Ensure Name is populated
        if (!record.containsKey("Name") || record.get("Name") == null) {
            if (record.containsKey("FirstName") || record.containsKey("LastName")) {
                String fn = Objects.toString(record.getOrDefault("FirstName", ""), "");
                String ln = Objects.toString(record.getOrDefault("LastName", ""), "");
                record.put("Name", (fn + " " + ln).trim());
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

        Map<String, Object> newRec = new LinkedHashMap<>();
        if ("Account".equalsIgnoreCase(canonicalObject)) {
            newRec.put("Id", "001mock" + String.format("%09d", count) + "SIM");
            newRec.put("Name", "Simulated Enterprise Corp " + count);
            newRec.put("Type", "Customer - Direct");
            newRec.put("Industry", "Software");
            newRec.put("AnnualRevenue", 15000000.0 + (count * 500000.0));
            newRec.put("Phone", "(555) 999-" + String.format("%04d", count));
            newRec.put("BillingCity", "Seattle");
            newRec.put("Website", "https://simulated" + count + ".example.com");
        } else if ("Contact".equalsIgnoreCase(canonicalObject)) {
            newRec.put("Id", "003mock" + String.format("%09d", count) + "SIM");
            newRec.put("FirstName", "Simulated");
            newRec.put("LastName", "Contact " + count);
            newRec.put("Name", "Simulated Contact " + count);
            newRec.put("Email", "simulated" + count + "@mock.example.com");
            newRec.put("Phone", "(555) 888-" + String.format("%04d", count));
            newRec.put("Title", "Director of Cloud Operations");
            newRec.put("Department", "Engineering");
            newRec.put("AccountId", "001mock000000001AAA");
        } else if ("Opportunity".equalsIgnoreCase(canonicalObject)) {
            newRec.put("Id", "006mock" + String.format("%09d", count) + "SIM");
            newRec.put("Name", "Simulated Cloud Expansion " + count);
            newRec.put("StageName", "Prospecting");
            newRec.put("Amount", 250000.0 + (count * 15000.0));
            newRec.put("CloseDate", "2026-12-31");
            newRec.put("Probability", 40.0);
            newRec.put("Type", "New Business");
            newRec.put("AccountId", "001mock000000001AAA");
        } else {
            newRec.put("Id", "00Qmock" + String.format("%09d", count) + "SIM");
            newRec.put("FirstName", "LeadFirst" + count);
            newRec.put("LastName", "Simulated" + count);
            newRec.put("Name", "LeadFirst" + count + " Simulated" + count);
            newRec.put("Company", "Simulated Ventures " + count);
            newRec.put("Email", "lead" + count + "@ventures.test");
            newRec.put("Phone", "(555) 777-" + String.format("%04d", count));
            newRec.put("Title", "Chief Innovation Officer");
            newRec.put("Status", "Open - Not Contacted");
        }

        newRec.put("CreatedDate", now);
        newRec.put("LastModifiedDate", now);
        newRec.put("SystemModstamp", now);

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
        return m;
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
        return m;
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
        return m;
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
        return m;
    }

    private void addField(ArrayNode fields, String name, String label, String type, boolean required, boolean creatable) {
        ObjectNode f = fields.addObject();
        f.put("name", name);
        f.put("label", label);
        f.put("type", type);
        f.put("nillable", !required);
        f.put("createable", creatable);
        f.put("deprecatedAndHidden", false);
    }

    private void addFieldWithRef(ArrayNode fields, String name, String label, String type, boolean required, boolean creatable, String refObject) {
        ObjectNode f = fields.addObject();
        f.put("name", name);
        f.put("label", label);
        f.put("type", type);
        f.put("nillable", !required);
        f.put("createable", creatable);
        f.put("deprecatedAndHidden", false);
        ArrayNode refs = f.putArray("referenceTo");
        refs.add(refObject);
    }
}
