package com.salesforce.sync.multitenancy;

public final class OrganizationContext {

    public static final String DEFAULT_ORGANIZATION_ID = "default_org";
    private static final ThreadLocal<String> CURRENT_ORG = new InheritableThreadLocal<>();

    private OrganizationContext() {}

    public static void setCurrentOrganization(String orgId) {
        if (orgId != null && !orgId.isBlank()) {
            CURRENT_ORG.set(orgId.trim());
        } else {
            CURRENT_ORG.remove();
        }
    }

    public static String getCurrentOrganization() {
        String orgId = CURRENT_ORG.get();
        return (orgId != null && !orgId.isBlank()) ? orgId : DEFAULT_ORGANIZATION_ID;
    }

    public static void clear() {
        CURRENT_ORG.remove();
    }
}
