package com.salesforce.sync.multitenancy;

public final class OrganizationContext {

    public static final String DEFAULT_ORGANIZATION_ID = "default_org";
    private static final ThreadLocal<String> CURRENT_ORG = new InheritableThreadLocal<>();

    private OrganizationContext() {}

    public static void setCurrentOrganization(String orgId) {
        if (isValidOrgId(orgId)) {
            CURRENT_ORG.set(orgId.trim());
        } else {
            CURRENT_ORG.remove();
        }
    }

    public static String getCurrentOrganization() {
        String orgId = CURRENT_ORG.get();
        return isValidOrgId(orgId) ? orgId.trim() : DEFAULT_ORGANIZATION_ID;
    }

    public static boolean isValidOrgId(String orgId) {
        return orgId != null && !orgId.isBlank()
                && !"undefined".equalsIgnoreCase(orgId.trim())
                && !"null".equalsIgnoreCase(orgId.trim());
    }

    public static void clear() {
        CURRENT_ORG.remove();
    }
}
