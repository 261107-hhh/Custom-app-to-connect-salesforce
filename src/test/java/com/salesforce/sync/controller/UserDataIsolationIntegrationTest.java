package com.salesforce.sync.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.sync.model.dto.RegisterRequest;
import com.salesforce.sync.model.entity.*;
import com.salesforce.sync.repository.*;
import com.salesforce.sync.service.SalesforceClientService;
import com.salesforce.sync.service.SalesforceSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class UserDataIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private OpportunityRepository opportunityRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private SalesforceSyncService syncService;

    @Autowired
    private SalesforceClientService sfClient;

    @Autowired
    private ObjectMapper objectMapper;

    private String userAToken;
    private final String userAEmail = "userA@enterprise.io";

    private String userBToken;
    private final String userBEmail = "userB@enterprise.io";

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        accountRepository.deleteAll();
        contactRepository.deleteAll();
        opportunityRepository.deleteAll();
        leadRepository.deleteAll();

        // Register User A
        RegisterRequest regA = new RegisterRequest(userAEmail, "Password123!", "User Alpha");
        String resA = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regA)))
                .andReturn().getResponse().getContentAsString();
        userAToken = objectMapper.readTree(resA).path("token").asText();

        // Register User B
        RegisterRequest regB = new RegisterRequest(userBEmail, "Password123!", "User Beta");
        String resB = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regB)))
                .andReturn().getResponse().getContentAsString();
        userBToken = objectMapper.readTree(resB).path("token").asText();
    }

    @Test
    void testCreatedDataIsStrictlyIsolatedBetweenUsers() throws Exception {
        // User A creates Account, Contact, Opportunity, Lead
        AccountEntity accA = new AccountEntity();
        accA.setId("acc-A-001");
        accA.setName("User A Corp");
        accA.markCreatedByCustomApp(userAEmail);
        accountRepository.save(accA);

        ContactEntity conA = new ContactEntity();
        conA.setId("con-A-001");
        conA.setName("Alice A");
        conA.setAccount(accA);
        conA.markCreatedByCustomApp(userAEmail);
        contactRepository.save(conA);

        OpportunityEntity oppA = new OpportunityEntity();
        oppA.setId("opp-A-001");
        oppA.setName("Deal A");
        oppA.setAccount(accA);
        oppA.markCreatedByCustomApp(userAEmail);
        opportunityRepository.save(oppA);

        LeadEntity leadA = new LeadEntity();
        leadA.setId("lead-A-001");
        leadA.setName("Lead A");
        leadA.setCompany("Company A");
        leadA.markCreatedByCustomApp(userAEmail);
        leadRepository.save(leadA);

        // 1. User A verifies they see their own records
        mockMvc.perform(get("/api/data/Account").header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].Id").value("acc-A-001"));

        mockMvc.perform(get("/api/data/Contact").header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].Id").value("con-A-001"));

        mockMvc.perform(get("/api/data/Opportunity").header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].Id").value("opp-A-001"));

        mockMvc.perform(get("/api/data/Lead").header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].Id").value("lead-A-001"));

        // 2. User B queries each table -> MUST BE 0 records!
        mockMvc.perform(get("/api/data/Account").header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(0));

        mockMvc.perform(get("/api/data/Contact").header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/data/Opportunity").header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/data/Lead").header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // 3. User B table counts -> 0
        mockMvc.perform(get("/api/data/tables").header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].count").value(0))
                .andExpect(jsonPath("$.data[1].count").value(0))
                .andExpect(jsonPath("$.data[2].count").value(0))
                .andExpect(jsonPath("$.data[3].count").value(0));

        // 4. User B cannot access User A's records by direct ID
        mockMvc.perform(get("/api/data/Account/acc-A-001").header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/data/Contact/con-A-001").header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/data/Opportunity/opp-A-001").header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/data/Lead/lead-A-001").header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound());

        // 5. Lookups for User B -> 0
        mockMvc.perform(get("/api/lookups/Account").header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void testSalesforceSyncAttributionAllowsSyncedUserToSeeData() throws Exception {
        // Connect to mock Salesforce
        sfClient.connect(Map.of("mode", "mock"));

        // User A runs sync
        syncService.syncSingleObject("Account", "full", Map.of(), userAEmail);

        // User A should now see the synced Salesforce records
        String resA = mockMvc.perform(get("/api/data/Account").header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThan(0)))
                .andReturn().getResponse().getContentAsString();

        int totalForA = objectMapper.readTree(resA).path("data").path("total").asInt();
        assertTrue(totalForA > 0);

        // User B has NOT synced yet -> MUST SEE 0 RECORDS!
        mockMvc.perform(get("/api/data/Account").header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(0));

        // Now User B connects and syncs
        syncService.syncSingleObject("Account", "full", Map.of(), userBEmail);

        // Now User B has synced with Salesforce, so User B CAN see the records!
        mockMvc.perform(get("/api/data/Account").header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(totalForA));
    }

    @Test
    void testRelatedRecordsIsolation() throws Exception {
        // Create an account for User A
        AccountEntity acc = new AccountEntity();
        acc.setId("rel-acc-001");
        acc.setName("Relational Parent");
        acc.markCreatedByCustomApp(userAEmail);
        accountRepository.save(acc);

        // Contact 1 belongs to User A
        ContactEntity con1 = new ContactEntity();
        con1.setId("rel-con-001");
        con1.setName("Alice UserA Contact");
        con1.setAccount(acc);
        con1.markCreatedByCustomApp(userAEmail);
        contactRepository.save(con1);

        // Contact 2 was created by User B under the same account in Salesforce
        ContactEntity con2 = new ContactEntity();
        con2.setId("rel-con-002");
        con2.setName("Bob UserB Contact");
        con2.setAccount(acc);
        con2.markCreatedByCustomApp(userBEmail);
        contactRepository.save(con2);

        // When User A queries related records for the account, User A should ONLY see Contact 1!
        mockMvc.perform(get("/api/data/Account/rel-acc-001/related").header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.name").value("Relational Parent"))
                .andExpect(jsonPath("$.data.contacts.length()").value(1))
                .andExpect(jsonPath("$.data.contacts[0].id").value("rel-con-001"));
    }
}
