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
class SalesforceClientServiceTest {

    @Autowired
    private SalesforceClientService clientService;

    @Test
    void testMockConnectionAndDescribeGlobal() throws Exception {
        Map<String, Object> connRes = clientService.connect(Map.of("mode", "mock"));
        assertTrue((Boolean) connRes.get("success"));
        assertEquals("mock", connRes.get("mode"));

        JsonNode globalDesc = clientService.describeGlobal();
        assertNotNull(globalDesc);
        assertTrue(globalDesc.has("sobjects"));
        assertTrue(globalDesc.path("sobjects").isArray());
        assertTrue(globalDesc.path("sobjects").size() >= 4);
    }

    @Test
    void testOAuthModeConnection() throws Exception {
        Map<String, Object> connRes = clientService.connect(Map.of(
                "mode", "oauth",
                "instanceUrl", "https://custom-domain.my.salesforce.com",
                "accessToken", "test-bearer-token-12345"
        ));
        assertTrue((Boolean) connRes.get("success"));
        assertEquals("oauth", connRes.get("mode"));
        assertEquals("https://custom-domain.my.salesforce.com", clientService.getInstanceUrl());
        assertTrue(clientService.isConnected());

        // Reconnect to mock mode for other tests
        clientService.connect(Map.of("mode", "mock"));
    }
}
