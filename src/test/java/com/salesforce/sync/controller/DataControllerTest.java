package com.salesforce.sync.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.sync.model.dto.RegisterRequest;
import com.salesforce.sync.model.entity.AccountEntity;
import com.salesforce.sync.repository.AccountRepository;
import com.salesforce.sync.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class DataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;
    private final String userEmail = "architect@enterprise.io";

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        accountRepository.deleteAll();

        RegisterRequest reg = new RegisterRequest(userEmail, "Password123!", "Lead Architect");
        String res = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn().getResponse().getContentAsString();
        userToken = objectMapper.readTree(res).path("token").asText();
    }

    @Test
    void testGetTables() throws Exception {
        // Setup 1 account for user
        AccountEntity acc = new AccountEntity();
        acc.setId("001tableTest");
        acc.setName("Table Check Corp");
        acc.markCreatedByCustomApp(userEmail);
        accountRepository.save(acc);

        mockMvc.perform(get("/api/data/tables")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].objectName").value("Account"))
                .andExpect(jsonPath("$.data[0].count").value(1));
    }

    @Test
    void testCreateRecordWithAuthenticatedUserAudit() throws Exception {
        Map<String, Object> fields = Map.of(
                "Name", "Quantum Stark Systems",
                "Type", "Customer - Direct",
                "Industry", "Technology",
                "AnnualRevenue", 75000000.0,
                "BillingCity", "New York"
        );

        String createRes = mockMvc.perform(post("/api/data/Account/create")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fields)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.createdBy").value(userEmail))
                .andReturn().getResponse().getContentAsString();

        String createdId = objectMapper.readTree(createRes).path("data").path("id").asText();
        assertNotNull(createdId);

        AccountEntity savedAcc = accountRepository.findById(createdId).orElse(null);
        assertNotNull(savedAcc);
        assertEquals("Quantum Stark Systems", savedAcc.getName());
        assertEquals(userEmail, savedAcc.getCustomAppCreatedBy());
        assertEquals(userEmail, savedAcc.getCustomAppModifiedBy());
        assertTrue(savedAcc.getIsCustomAppCreated());
        assertTrue(savedAcc.isAssociatedWithUser(userEmail));
        assertEquals(75000000.0, savedAcc.getAnnualRevenue());
        assertNotNull(savedAcc.getRawData());
    }

    @Test
    void testCreateRecordWithoutTokenFallsBackToAnonymous() throws Exception {
        Map<String, Object> fields = Map.of(
                "Name", "Guest Corporation",
                "Industry", "Finance"
        );

        String createRes = mockMvc.perform(post("/api/data/Account/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fields)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.createdBy").value("anonymous@app.local"))
                .andReturn().getResponse().getContentAsString();

        String createdId = objectMapper.readTree(createRes).path("data").path("id").asText();
        AccountEntity savedAcc = accountRepository.findById(createdId).orElse(null);
        assertNotNull(savedAcc);
        assertEquals("anonymous@app.local", savedAcc.getCustomAppCreatedBy());
    }

    @Test
    void testQueryRecordsWithSearch() throws Exception {
        AccountEntity acc = new AccountEntity();
        acc.setId("001testQuery001");
        acc.setName("Cyberdyne Dynamics");
        acc.setBillingCity("Los Angeles");
        acc.markCreatedByCustomApp(userEmail);
        accountRepository.save(acc);

        mockMvc.perform(get("/api/data/Account?search=Cyberdyne")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].Name").value("Cyberdyne Dynamics"));
    }

    @Test
    void testUserIsolationPreventsCrossUserAccess() throws Exception {
        // 1. Create a record associated with User A
        AccountEntity accA = new AccountEntity();
        accA.setId("001userA");
        accA.setName("User A Confidential Account");
        accA.markCreatedByCustomApp(userEmail);
        accountRepository.save(accA);

        // 2. Register User B
        String userBEmail = "userB@enterprise.io";
        RegisterRequest regB = new RegisterRequest(userBEmail, "Password123!", "User B");
        String resB = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regB)))
                .andReturn().getResponse().getContentAsString();
        String userBToken = objectMapper.readTree(resB).path("token").asText();

        // 3. User B queries records -> should see 0 records (isolation active!)
        mockMvc.perform(get("/api/data/Account")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(0));

        // 4. User B attempts to access User A's record directly by ID -> should return 404 Not Found
        mockMvc.perform(get("/api/data/Account/001userA")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound());

        // 5. User B simulates syncing User A's record from Salesforce
        accA.addSyncedBy(userBEmail);
        accountRepository.save(accA);

        // 6. Now that User B has synced it from Salesforce, User B should see it!
        mockMvc.perform(get("/api/data/Account")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].Id").value("001userA"));

        // 7. And User B can now get it by ID
        mockMvc.perform(get("/api/data/Account/001userA")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("001userA"));
    }
}
