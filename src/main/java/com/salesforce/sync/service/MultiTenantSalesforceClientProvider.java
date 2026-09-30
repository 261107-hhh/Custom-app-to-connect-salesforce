package com.salesforce.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.sync.model.entity.OrganizationEntity;
import com.salesforce.sync.multitenancy.OrganizationContext;
import com.salesforce.sync.repository.OrganizationRepository;
import com.salesforce.sync.security.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MultiTenantSalesforceClientProvider {

    private static final Logger log = LoggerFactory.getLogger(MultiTenantSalesforceClientProvider.class);

    private final SalesforceClientService defaultClient;
    private final OrganizationRepository orgRepo;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final MockSalesforceService mockSalesforceService;

    private final Map<String, SalesforceClientService> clientCache = new ConcurrentHashMap<>();

    public MultiTenantSalesforceClientProvider(SalesforceClientService defaultClient,
                                              OrganizationRepository orgRepo,
                                              EncryptionService encryptionService,
                                              ObjectMapper objectMapper,
                                              MockSalesforceService mockSalesforceService) {
        this.defaultClient = defaultClient;
        this.orgRepo = orgRepo;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
        this.mockSalesforceService = mockSalesforceService;
    }

    public SalesforceClientService getClientForCurrentOrganization() {
        String orgId = OrganizationContext.getCurrentOrganization();
        return getClientForOrganization(orgId);
    }

    public SalesforceClientService getClientForOrganization(String orgId) {
        String effectiveOrg = (orgId != null && !orgId.isBlank()) ? orgId.trim() : OrganizationContext.DEFAULT_ORGANIZATION_ID;

        // For default_org, use defaultClient unless an explicit non-disconnected tenant integration is configured
        if (OrganizationContext.DEFAULT_ORGANIZATION_ID.equalsIgnoreCase(effectiveOrg)) {
            Optional<OrganizationEntity> defaultOrgOpt = orgRepo.findById(OrganizationContext.DEFAULT_ORGANIZATION_ID);
            if (defaultOrgOpt.isEmpty() || defaultOrgOpt.get().getSfAuthMode() == null || "disconnected".equalsIgnoreCase(defaultOrgOpt.get().getSfAuthMode())) {
                return defaultClient;
            }
        }

        // If organization is not found in database, fall back to defaultClient (e.g. for standalone tests)
        Optional<OrganizationEntity> orgOpt = orgRepo.findById(effectiveOrg);
        if (orgOpt.isEmpty()) {
            return defaultClient;
        }

        return clientCache.computeIfAbsent(effectiveOrg, id -> {
            Optional<OrganizationEntity> currentOrgOpt = orgRepo.findById(id);
            if (currentOrgOpt.isEmpty()) {
                log.warn("Organization {} not found in database, falling back to default client", id);
                return defaultClient;
            }

            OrganizationEntity org = currentOrgOpt.get();
            String mode = org.getSfAuthMode();

            if (mode == null || "disconnected".equalsIgnoreCase(mode)) {
                SalesforceClientService client = new SalesforceClientService(objectMapper, null, mockSalesforceService);
                client.disconnect();
                return client;
            }

            if ("mock".equalsIgnoreCase(mode)) {
                SalesforceClientService client = new SalesforceClientService(objectMapper, null, mockSalesforceService);
                try {
                    client.connect(Map.of("mode", "mock"));
                } catch (Exception ignored) {}
                return client;
            }

            log.info("Creating dedicated SalesforceClientService for organization {}", id);
            SalesforceClientService client = new SalesforceClientService(objectMapper, null, mockSalesforceService);

            Map<String, Object> creds = new HashMap<>();
            creds.put("mode", mode);
            creds.put("instanceUrl", org.getSfInstanceUrl());

            if ("eca".equalsIgnoreCase(mode)) {
                creds.put("clientId", org.getSfClientId());
                if (org.getSfClientSecretEncrypted() != null) {
                    creds.put("clientSecret", encryptionService.decrypt(org.getSfClientSecretEncrypted()));
                }
            } else if ("password".equalsIgnoreCase(mode)) {
                creds.put("username", org.getSfUsername());
                if (org.getSfPasswordEncrypted() != null) {
                    creds.put("password", encryptionService.decrypt(org.getSfPasswordEncrypted()));
                }
                if (org.getSfSecurityTokenEncrypted() != null) {
                    creds.put("securityToken", encryptionService.decrypt(org.getSfSecurityTokenEncrypted()));
                }
            }

            try {
                client.connect(creds);
            } catch (Exception e) {
                log.error("Failed to connect Salesforce client for organization {}: {}", id, e.getMessage());
                throw new RuntimeException("Failed to connect Salesforce client for organization " + id + ": " + e.getMessage(), e);
            }

            return client;
        });
    }

    public void evictCache(String orgId) {
        if (orgId != null) {
            SalesforceClientService removed = clientCache.remove(orgId);
            if (removed != null) {
                log.info("Evicted Salesforce client cache for organization {}", orgId);
            }
        }
    }
}
