package com.salesforce.sync.multitenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class HibernateTenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return OrganizationContext.getCurrentOrganization();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
