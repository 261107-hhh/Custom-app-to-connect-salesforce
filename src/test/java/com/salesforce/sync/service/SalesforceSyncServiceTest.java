package com.salesforce.sync.service;

import com.salesforce.sync.model.entity.AccountEntity;
import com.salesforce.sync.model.entity.LeadEntity;
import com.salesforce.sync.repository.AccountRepository;
import com.salesforce.sync.repository.LeadRepository;
import com.salesforce.sync.repository.SyncStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class SalesforceSyncServiceTest {

    @Autowired
    private SalesforceSyncService syncService;

    @Autowired
    private SalesforceClientService clientService;

    @Autowired
    private MockSalesforceService mockService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private SyncStateRepository stateRepository;

    @BeforeEach
    void setUp() throws Exception {
        clientService.connect(Map.of("mode", "mock"));
    }

    @Test
    void testFullSyncAccount() throws Exception {
        int count = syncService.syncSingleObject("Account", "full", Map.of());
        assertTrue(count >= 3);
        assertTrue(accountRepository.count() >= 3);
    }

    @Test
    void testFullSyncLeadIncludesTitle() throws Exception {
        int count = syncService.syncSingleObject("Lead", "full", Map.of());
        assertTrue(count >= 2);
        assertTrue(leadRepository.count() >= 2);

        LeadEntity lead = leadRepository.findAll().stream()
                .filter(l -> l.getTitle() != null && !l.getTitle().isBlank())
                .findFirst()
                .orElse(null);
        assertNotNull(lead);
        assertNotNull(lead.getTitle());
    }

    @Test
    void testIncrementalSyncDeltaDetection() throws Exception {
        stateRepository.deleteAll();

        // 1. Initial sync
        int firstCount = syncService.syncSingleObject("Account", "incremental", Map.of());
        assertTrue(firstCount > 0);

        // 2. Immediate incremental sync should return 0 new records
        int secondCount = syncService.syncSingleObject("Account", "incremental", Map.of());
        assertEquals(0, secondCount, "Immediate subsequent sync should detect 0 new or modified records");

        // 3. Simulate a new record in Mock Salesforce
        Map<String, Object> simRec = mockService.simulateNewOrModifiedRecord("Account");
        assertNotNull(simRec);

        // 4. Incremental sync should now pull exactly 1 new record
        int thirdCount = syncService.syncSingleObject("Account", "incremental", Map.of());
        assertEquals(1, thirdCount, "Incremental sync should detect exactly 1 newly simulated record");
    }

    @Test
    void testAuditPreservationDuringSalesforceSync() throws Exception {
        // Pre-create an account locally with Custom App attribution
        String targetId = "001mock000000001AAA";
        AccountEntity localAcc = accountRepository.findById(targetId).orElse(new AccountEntity());
        localAcc.setId(targetId);
        localAcc.setName("Acme Corporation (Custom App Edition)");
        localAcc.markCreatedByCustomApp("lead.architect@enterprise.io");
        accountRepository.save(localAcc);

        // Run Salesforce Sync which updates the record
        syncService.syncSingleObject("Account", "full", Map.of());

        // Verify that customAppCreatedBy is PRESERVED and NOT overwritten!
        AccountEntity syncedAcc = accountRepository.findById(targetId).orElse(null);
        assertNotNull(syncedAcc);
        assertEquals("lead.architect@enterprise.io", syncedAcc.getCustomAppCreatedBy(),
                "Salesforce Sync must NEVER overwrite local custom_app_created_by audit attribution!");
        assertEquals("lead.architect@enterprise.io", syncedAcc.getCustomAppModifiedBy());
        assertTrue(syncedAcc.getIsCustomAppCreated());
    }
}
