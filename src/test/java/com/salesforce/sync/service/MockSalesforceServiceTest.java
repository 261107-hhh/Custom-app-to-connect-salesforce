package com.salesforce.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class MockSalesforceServiceTest {

    @Autowired
    private MockSalesforceService mockService;

    @Test
    void testDescribeAllObjects() {
        for (String obj : new String[]{"Account", "Contact", "Opportunity", "Lead"}) {
            JsonNode describe = mockService.describeObject(obj);
            assertNotNull(describe);
            assertEquals(obj, describe.path("name").asText());
            assertTrue(describe.path("fields").isArray());
            assertTrue(describe.path("fields").size() >= 5);
        }
    }

    @Test
    void testQueryDeltaFiltering() {
        JsonNode allAccounts = mockService.query("SELECT Id, Name, SystemModstamp FROM Account");
        int total = allAccounts.path("totalSize").asInt();
        assertTrue(total >= 3);

        // Query with future timestamp returns 0
        JsonNode futureAccounts = mockService.query("SELECT Id FROM Account WHERE SystemModstamp > 2099-01-01T00:00:00.000Z");
        assertEquals(0, futureAccounts.path("totalSize").asInt());
    }

    @Test
    void testSimulateNewRecordIncreasesCount() {
        JsonNode before = mockService.query("SELECT Id FROM Contact");
        int countBefore = before.path("totalSize").asInt();

        Map<String, Object> simContact = mockService.simulateNewOrModifiedRecord("Contact");
        assertNotNull(simContact);
        assertTrue(simContact.get("Id").toString().startsWith("003mock"));

        JsonNode after = mockService.query("SELECT Id FROM Contact");
        assertEquals(countBefore + 1, after.path("totalSize").asInt());
    }

    @Test
    void testCreateRecordAddsToStore() {
        Map<String, Object> newAcc = mockService.createRecord("Account", Map.of(
                "Name", "Hyperion Dynamics",
                "Industry", "Aerospace"
        ));
        assertNotNull(newAcc);
        String id = (String) newAcc.get("id");
        assertTrue(id.startsWith("001mock"));

        JsonNode query = mockService.query("SELECT Id, Name FROM Account WHERE Name LIKE '%Hyperion%'");
        assertTrue(query.path("totalSize").asInt() >= 1);
    }
}
