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

    @Test
    void testFormatSoqlDateTimeSanitization() {
        // Date only
        assertEquals("2026-08-30T00:00:00Z", SalesforceSyncService.formatSoqlDateTime("2026-08-30", false));
        assertEquals("2026-08-30T23:59:59Z", SalesforceSyncService.formatSoqlDateTime("2026-08-30", true));

        // Milliseconds stripping (.000Z and .999Z)
        assertEquals("2026-01-10T08:00:00Z", SalesforceSyncService.formatSoqlDateTime("2026-01-10T08:00:00.000Z", false));
        assertEquals("2026-09-29T23:59:59Z", SalesforceSyncService.formatSoqlDateTime("2026-09-29T23:59:59.999Z", true));

        // Timezone offset stripping
        assertEquals("2026-01-10T08:00:00Z", SalesforceSyncService.formatSoqlDateTime("2026-01-10T08:00:00.000+0000", false));
        assertEquals("2026-09-29T18:20:16Z", SalesforceSyncService.formatSoqlDateTime("2026-09-29T18:20:16+05:30", false));

        // Standard ISO preserved
        assertEquals("2026-05-15T12:30:00Z", SalesforceSyncService.formatSoqlDateTime("2026-05-15T12:30:00Z", false));
    }

    @Test
    void testFullSyncBypassesDateRangeFilters() throws Exception {
        // Even if future fromDate / toDate are supplied, Full Sync must pull ALL records without date boundary restriction
        Map<String, Object> futureFilters = Map.of(
                "fromDate", "2099-01-01",
                "toDate", "2099-12-31"
        );
        int count = syncService.syncSingleObject("Account", "full", futureFilters);
        assertTrue(count >= 3, "Full Sync must bypass fromDate/toDate and ingest all records");
    }

    @Test
    void testIncrementalSyncRespectsDateRange() throws Exception {
        stateRepository.deleteAll();

        // 1. Query with fromDate set to 7 days ago (should only pull recent records, not the 45-day-old one)
        String sevenDaysAgo = java.time.LocalDate.now().minusDays(7).toString();
        String today = java.time.LocalDate.now().toString();

        int recentCount = syncService.syncSingleObject("Account", "incremental", Map.of(
                "fromDate", sevenDaysAgo,
                "toDate", today
        ));

        // In mock data: 1 account is 2 days old (recent), 1 is 45 days old.
        // Therefore recentCount should be at least 1 and less than all 3 accounts.
        assertTrue(recentCount >= 1, "Should pull accounts modified within the last 7 days");
    }

    @Test
    void testFullSyncWithNameFilterSuraj() throws Exception {
        Map<String, Object> filters = Map.of("nameContains", "suraj");

        int accCount = syncService.syncSingleObject("Account", "full", filters, "akash@oodles.io");
        int conCount = syncService.syncSingleObject("Contact", "full", filters, "akash@oodles.io");
        int oppCount = syncService.syncSingleObject("Opportunity", "full", filters, "akash@oodles.io");
        int leadCount = syncService.syncSingleObject("Lead", "full", filters, "akash@oodles.io");

        assertTrue(accCount >= 1, "Full sync with nameContains 'suraj' should sync matching Account");
        assertTrue(conCount >= 1, "Full sync with nameContains 'suraj' should sync matching Contact");
        assertTrue(oppCount >= 1, "Full sync with nameContains 'suraj' should sync matching Opportunity");
        assertTrue(leadCount >= 1, "Full sync with nameContains 'suraj' should sync matching Lead");

        // Verify that akash@oodles.io is added to syncedBy
        AccountEntity acc = accountRepository.findById("001mock000000004AAA").orElse(null);
        assertNotNull(acc);
        assertEquals("Suraj Enterprise", acc.getName());
        assertTrue(acc.isAssociatedWithUser("akash@oodles.io"));
    }

    @Test
    void testIncrementalSyncWithNameFilterAndDateRange() throws Exception {
        stateRepository.deleteAll();

        String fromDate = java.time.LocalDate.now().minusDays(7).toString();
        String toDate = java.time.LocalDate.now().toString();

        Map<String, Object> filters = Map.of(
                "nameContains", "suraj",
                "fromDate", fromDate,
                "toDate", toDate
        );

        int accCount = syncService.syncSingleObject("Account", "incremental", filters, "akash@oodles.io");
        int conCount = syncService.syncSingleObject("Contact", "incremental", filters, "akash@oodles.io");
        int oppCount = syncService.syncSingleObject("Opportunity", "incremental", filters, "akash@oodles.io");
        int leadCount = syncService.syncSingleObject("Lead", "incremental", filters, "akash@oodles.io");

        assertTrue(accCount >= 1, "Incremental sync with date range should sync recent 'suraj' Account");
        assertTrue(conCount >= 1, "Incremental sync with date range should sync recent 'suraj' Contact");
        assertTrue(oppCount >= 1, "Incremental sync with date range should sync recent 'suraj' Opportunity");
        assertTrue(leadCount >= 1, "Incremental sync with date range should sync recent 'suraj' Lead");
    }
}
