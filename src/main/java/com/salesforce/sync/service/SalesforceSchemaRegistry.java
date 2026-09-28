package com.salesforce.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Registry and normalization helper for Salesforce SObject schemas.
 * Ensures all standard and custom fields are accounted for, whether they have
 * values or are null/empty.
 */
@Component
public class SalesforceSchemaRegistry {

    private static final Map<String, List<FieldMeta>> STANDARD_SCHEMA = new LinkedHashMap<>();

    public record FieldMeta(String name, String label, String type, boolean required, boolean createable, String refObject) {
        public FieldMeta(String name, String label, String type, boolean required, boolean createable) {
            this(name, label, type, required, createable, null);
        }
    }

    static {
        // --- ACCOUNT SCHEMA ---
        List<FieldMeta> accFields = new ArrayList<>();
        accFields.add(new FieldMeta("Id", "Record ID", "id", false, false));
        accFields.add(new FieldMeta("Name", "Account Name", "string", true, true));
        accFields.add(new FieldMeta("Type", "Account Type", "picklist", false, true));
        accFields.add(new FieldMeta("ParentId", "Parent Account ID", "reference", false, true, "Account"));
        accFields.add(new FieldMeta("BillingStreet", "Billing Street", "textarea", false, true));
        accFields.add(new FieldMeta("BillingCity", "Billing City", "string", false, true));
        accFields.add(new FieldMeta("BillingState", "Billing State/Province", "string", false, true));
        accFields.add(new FieldMeta("BillingPostalCode", "Billing Zip/Postal Code", "string", false, true));
        accFields.add(new FieldMeta("BillingCountry", "Billing Country", "string", false, true));
        accFields.add(new FieldMeta("ShippingStreet", "Shipping Street", "textarea", false, true));
        accFields.add(new FieldMeta("ShippingCity", "Shipping City", "string", false, true));
        accFields.add(new FieldMeta("ShippingState", "Shipping State/Province", "string", false, true));
        accFields.add(new FieldMeta("ShippingPostalCode", "Shipping Zip/Postal Code", "string", false, true));
        accFields.add(new FieldMeta("ShippingCountry", "Shipping Country", "string", false, true));
        accFields.add(new FieldMeta("Phone", "Phone", "phone", false, true));
        accFields.add(new FieldMeta("Fax", "Fax", "phone", false, true));
        accFields.add(new FieldMeta("AccountNumber", "Account Number", "string", false, true));
        accFields.add(new FieldMeta("Website", "Website", "url", false, true));
        accFields.add(new FieldMeta("Sic", "SIC Code", "string", false, true));
        accFields.add(new FieldMeta("Industry", "Industry", "picklist", false, true));
        accFields.add(new FieldMeta("AnnualRevenue", "Annual Revenue", "currency", false, true));
        accFields.add(new FieldMeta("NumberOfEmployees", "Employees", "int", false, true));
        accFields.add(new FieldMeta("Ownership", "Ownership", "picklist", false, true));
        accFields.add(new FieldMeta("TickerSymbol", "Ticker Symbol", "string", false, true));
        accFields.add(new FieldMeta("Description", "Account Description", "textarea", false, true));
        accFields.add(new FieldMeta("Rating", "Account Rating", "picklist", false, true));
        accFields.add(new FieldMeta("Site", "Account Site", "string", false, true));
        accFields.add(new FieldMeta("CreatedDate", "Created Date", "datetime", false, false));
        accFields.add(new FieldMeta("LastModifiedDate", "Last Modified Date", "datetime", false, false));
        accFields.add(new FieldMeta("SystemModstamp", "System Modstamp", "datetime", false, false));
        STANDARD_SCHEMA.put("Account", Collections.unmodifiableList(accFields));

        // --- CONTACT SCHEMA ---
        List<FieldMeta> conFields = new ArrayList<>();
        conFields.add(new FieldMeta("Id", "Record ID", "id", false, false));
        conFields.add(new FieldMeta("AccountId", "Account ID", "reference", false, true, "Account"));
        conFields.add(new FieldMeta("LastName", "Last Name", "string", true, true));
        conFields.add(new FieldMeta("FirstName", "First Name", "string", false, true));
        conFields.add(new FieldMeta("Salutation", "Salutation", "picklist", false, true));
        conFields.add(new FieldMeta("Name", "Full Name", "string", false, false));
        conFields.add(new FieldMeta("OtherStreet", "Other Street", "textarea", false, true));
        conFields.add(new FieldMeta("OtherCity", "Other City", "string", false, true));
        conFields.add(new FieldMeta("OtherState", "Other State/Province", "string", false, true));
        conFields.add(new FieldMeta("OtherPostalCode", "Other Zip/Postal Code", "string", false, true));
        conFields.add(new FieldMeta("OtherCountry", "Other Country", "string", false, true));
        conFields.add(new FieldMeta("MailingStreet", "Mailing Street", "textarea", false, true));
        conFields.add(new FieldMeta("MailingCity", "Mailing City", "string", false, true));
        conFields.add(new FieldMeta("MailingState", "Mailing State/Province", "string", false, true));
        conFields.add(new FieldMeta("MailingPostalCode", "Mailing Zip/Postal Code", "string", false, true));
        conFields.add(new FieldMeta("MailingCountry", "Mailing Country", "string", false, true));
        conFields.add(new FieldMeta("Phone", "Business Phone", "phone", false, true));
        conFields.add(new FieldMeta("Fax", "Business Fax", "phone", false, true));
        conFields.add(new FieldMeta("MobilePhone", "Mobile Phone", "phone", false, true));
        conFields.add(new FieldMeta("HomePhone", "Home Phone", "phone", false, true));
        conFields.add(new FieldMeta("OtherPhone", "Other Phone", "phone", false, true));
        conFields.add(new FieldMeta("AssistantPhone", "Assistant Phone", "phone", false, true));
        conFields.add(new FieldMeta("ReportsToId", "Reports To ID", "reference", false, true, "Contact"));
        conFields.add(new FieldMeta("Email", "Email", "email", false, true));
        conFields.add(new FieldMeta("Title", "Title", "string", false, true));
        conFields.add(new FieldMeta("Department", "Department", "string", false, true));
        conFields.add(new FieldMeta("AssistantName", "Assistant's Name", "string", false, true));
        conFields.add(new FieldMeta("LeadSource", "Lead Source", "picklist", false, true));
        conFields.add(new FieldMeta("Birthdate", "Birthdate", "date", false, true));
        conFields.add(new FieldMeta("Description", "Contact Description", "textarea", false, true));
        conFields.add(new FieldMeta("CreatedDate", "Created Date", "datetime", false, false));
        conFields.add(new FieldMeta("LastModifiedDate", "Last Modified Date", "datetime", false, false));
        conFields.add(new FieldMeta("SystemModstamp", "System Modstamp", "datetime", false, false));
        STANDARD_SCHEMA.put("Contact", Collections.unmodifiableList(conFields));

        // --- OPPORTUNITY SCHEMA ---
        List<FieldMeta> oppFields = new ArrayList<>();
        oppFields.add(new FieldMeta("Id", "Record ID", "id", false, false));
        oppFields.add(new FieldMeta("AccountId", "Account ID", "reference", false, true, "Account"));
        oppFields.add(new FieldMeta("Name", "Opportunity Name", "string", true, true));
        oppFields.add(new FieldMeta("Description", "Description", "textarea", false, true));
        oppFields.add(new FieldMeta("StageName", "Stage", "picklist", true, true));
        oppFields.add(new FieldMeta("Amount", "Amount", "currency", false, true));
        oppFields.add(new FieldMeta("Probability", "Probability (%)", "percent", false, true));
        oppFields.add(new FieldMeta("ExpectedRevenue", "Expected Revenue", "currency", false, false));
        oppFields.add(new FieldMeta("CloseDate", "Close Date", "date", true, true));
        oppFields.add(new FieldMeta("Type", "Opportunity Type", "picklist", false, true));
        oppFields.add(new FieldMeta("NextStep", "Next Step", "string", false, true));
        oppFields.add(new FieldMeta("LeadSource", "Lead Source", "picklist", false, true));
        oppFields.add(new FieldMeta("IsClosed", "Closed", "boolean", false, false));
        oppFields.add(new FieldMeta("IsWon", "Won", "boolean", false, false));
        oppFields.add(new FieldMeta("ForecastCategoryName", "Forecast Category", "picklist", false, true));
        oppFields.add(new FieldMeta("CampaignId", "Campaign ID", "reference", false, true, "Campaign"));
        oppFields.add(new FieldMeta("CreatedDate", "Created Date", "datetime", false, false));
        oppFields.add(new FieldMeta("LastModifiedDate", "Last Modified Date", "datetime", false, false));
        oppFields.add(new FieldMeta("SystemModstamp", "System Modstamp", "datetime", false, false));
        STANDARD_SCHEMA.put("Opportunity", Collections.unmodifiableList(oppFields));

        // --- LEAD SCHEMA ---
        List<FieldMeta> leadFields = new ArrayList<>();
        leadFields.add(new FieldMeta("Id", "Record ID", "id", false, false));
        leadFields.add(new FieldMeta("LastName", "Last Name", "string", true, true));
        leadFields.add(new FieldMeta("FirstName", "First Name", "string", false, true));
        leadFields.add(new FieldMeta("Salutation", "Salutation", "picklist", false, true));
        leadFields.add(new FieldMeta("Name", "Full Name", "string", false, false));
        leadFields.add(new FieldMeta("Title", "Title", "string", false, true));
        leadFields.add(new FieldMeta("Company", "Company", "string", true, true));
        leadFields.add(new FieldMeta("Street", "Street", "textarea", false, true));
        leadFields.add(new FieldMeta("City", "City", "string", false, true));
        leadFields.add(new FieldMeta("State", "State/Province", "string", false, true));
        leadFields.add(new FieldMeta("PostalCode", "Zip/Postal Code", "string", false, true));
        leadFields.add(new FieldMeta("Country", "Country", "string", false, true));
        leadFields.add(new FieldMeta("Phone", "Phone", "phone", false, true));
        leadFields.add(new FieldMeta("MobilePhone", "Mobile Phone", "phone", false, true));
        leadFields.add(new FieldMeta("Fax", "Fax", "phone", false, true));
        leadFields.add(new FieldMeta("Email", "Email", "email", false, true));
        leadFields.add(new FieldMeta("Website", "Website", "url", false, true));
        leadFields.add(new FieldMeta("Description", "Description", "textarea", false, true));
        leadFields.add(new FieldMeta("LeadSource", "Lead Source", "picklist", false, true));
        leadFields.add(new FieldMeta("Status", "Status", "picklist", true, true));
        leadFields.add(new FieldMeta("Industry", "Industry", "picklist", false, true));
        leadFields.add(new FieldMeta("Rating", "Rating", "picklist", false, true));
        leadFields.add(new FieldMeta("AnnualRevenue", "Annual Revenue", "currency", false, true));
        leadFields.add(new FieldMeta("NumberOfEmployees", "Employees", "int", false, true));
        leadFields.add(new FieldMeta("CreatedDate", "Created Date", "datetime", false, false));
        leadFields.add(new FieldMeta("LastModifiedDate", "Last Modified Date", "datetime", false, false));
        leadFields.add(new FieldMeta("SystemModstamp", "System Modstamp", "datetime", false, false));
        STANDARD_SCHEMA.put("Lead", Collections.unmodifiableList(leadFields));
    }

    /**
     * Returns standard field metadata for supported standard objects.
     */
    public List<FieldMeta> getStandardFields(String objectName) {
        String canonical = canonicalName(objectName);
        return STANDARD_SCHEMA.getOrDefault(canonical, List.of());
    }

    /**
     * Extracts all queryable, non-compound field names from a describe JsonNode or fallback.
     * Compound fields like 'Address', 'BillingAddress', etc. are excluded because SOQL does
     * not allow selecting compound fields directly, but their constituent components
     * (e.g. 'BillingStreet', 'BillingCity') are included.
     */
    public List<String> extractQueryableFields(String objectName, JsonNode describeNode) {
        Set<String> fields = new LinkedHashSet<>();

        // Start with standard fields as base guarantee
        for (FieldMeta f : getStandardFields(objectName)) {
            fields.add(f.name());
        }

        if (describeNode != null && describeNode.has("fields") && describeNode.path("fields").isArray()) {
            for (JsonNode f : describeNode.path("fields")) {
                String type = f.path("type").asText("").toLowerCase();
                String name = f.path("name").asText("");

                // Skip compound address/location or binary types which cannot be queried in standard SOQL
                if ("address".equals(type) || "location".equals(type) || "base64".equals(type)) {
                    continue;
                }

                // If queryable attribute exists and is false, skip
                if (f.has("queryable") && !f.path("queryable").asBoolean(true)) {
                    continue;
                }

                if (!name.isBlank()) {
                    fields.add(name);
                }
            }
        }

        return new ArrayList<>(fields);
    }

    /**
     * Ensures every field in the object's schema is explicitly present in the record map.
     * If a field is not present or was null/empty in the incoming map, it is explicitly set to null.
     */
    public Map<String, Object> normalizeRecord(String objectName, Map<String, Object> record, JsonNode describeNode) {
        Map<String, Object> normalized = new LinkedHashMap<>();

        // 1. Get all known field names from schema
        List<String> schemaFields = extractQueryableFields(objectName, describeNode);

        // 2. Put every schema field (preserving existing value or setting null)
        for (String fieldName : schemaFields) {
            if (record != null && record.containsKey(fieldName)) {
                normalized.put(fieldName, record.get(fieldName));
            } else if (record != null && record.containsKey(fieldName.toLowerCase())) {
                normalized.put(fieldName, record.get(fieldName.toLowerCase()));
            } else {
                normalized.put(fieldName, null);
            }
        }

        // 3. Put any additional custom or audit fields that were in the record but not in standard schema
        if (record != null) {
            for (Map.Entry<String, Object> entry : record.entrySet()) {
                if (!normalized.containsKey(entry.getKey())) {
                    normalized.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return normalized;
    }

    public static String canonicalName(String objectName) {
        if (objectName == null || objectName.isBlank()) return "Account";
        String lower = objectName.toLowerCase();
        return switch (lower) {
            case "account" -> "Account";
            case "contact" -> "Contact";
            case "opportunity" -> "Opportunity";
            case "lead" -> "Lead";
            default -> Character.toUpperCase(objectName.charAt(0)) + objectName.substring(1);
        };
    }
}
