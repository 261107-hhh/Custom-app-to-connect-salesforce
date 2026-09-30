package com.salesforce.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesforce.sync.model.entity.OrganizationEntity;
import com.salesforce.sync.multitenancy.OrganizationContext;
import com.salesforce.sync.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
public class SalesforceLiveSyncAndAntiPollutionTest {

    @Autowired
    private SalesforceSyncService syncService;

    @Autowired
    private MultiTenantSalesforceClientProvider clientProvider;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String ORG_LIVE = "org_live-test";

    @BeforeEach
    void setUp() {
        OrganizationContext.setCurrentOrganization(ORG_LIVE);
        OrganizationEntity org = new OrganizationEntity(ORG_LIVE, "Live Corp", "live-corp");
        org.setSfAuthMode("eca");
        org.setSfInstanceUrl("https://oodlestechnologies2-dev-ed.develop.my.salesforce.com");
        org.setSfClientId("3MVG9PwZx9R6_UrekKr6tBkmZrKgb5xq68Q0GXcREh5fhL0P_paSi02QUVuITgH_L32nAs6lYrTJdfQ_lTTlM");
        org.setSfClientSecretEncrypted("0NDGtahjBGJvoGL6Z86cPByxbQGkVq+W5p2X+GP9ht9uenBcSWqFmzlODLl2+Ys7YFiYIqQ0Tojxx/ZR3kEc1JfV6HEdH1c7LtcZkgE7gydsfwW5F/FGbd0W8lU=");
        organizationRepository.save(org);
        clientProvider.evictCache(ORG_LIVE);
    }

    @AfterEach
    void tearDown() {
        clientProvider.evictCache(ORG_LIVE);
        organizationRepository.deleteById(ORG_LIVE);
        OrganizationContext.clear();
    }

    @Test
    void testTenantUsesLiveSalesforceClientAndNeverMock() {
        SalesforceClientService client = clientProvider.getClientForOrganization(ORG_LIVE);
        assertNotNull(client);
        assertTrue(client.isConnected(), "Tenant client should be connected");
        assertFalse(client.isMock(), "Tenant client must NOT be mock when configured with ECA");
        assertEquals("https://oodlestechnologies2-dev-ed.develop.my.salesforce.com", client.getInstanceUrl());
    }

    @Test
    void testAntiPollutionBlocksMockRecordsFromEnteringDatabase() throws Exception {
        SalesforceClientService liveClient = clientProvider.getClientForOrganization(ORG_LIVE);
        assertFalse(liveClient.isMock());

        // Trigger sync for Account using live client
        int count = syncService.syncSingleObject("Account", "full", Map.of(), "admin@oodles.io", ORG_LIVE);
        assertTrue(count > 0, "Live Salesforce sync should fetch real records from org");

        // Verify NO mock records were saved into the database
        boolean hasMock = accountRepository.findAll().stream().anyMatch(a -> a.getId().contains("mock"));
        assertFalse(hasMock, "Database MUST NOT contain any mock records!");
    }
}
