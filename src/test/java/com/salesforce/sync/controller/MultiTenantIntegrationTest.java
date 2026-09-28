package com.salesforce.sync.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.sync.model.dto.*;
import com.salesforce.sync.model.entity.*;
import com.salesforce.sync.multitenancy.OrganizationContext;
import com.salesforce.sync.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class MultiTenantIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository memberRepository;

    @Autowired
    private OrganizationInvitationRepository invitationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        OrganizationContext.clear();
    }

    @AfterEach
    void tearDown() {
        OrganizationContext.clear();
    }

    @Test
    void testRegisterOrganizationAndLogin() throws Exception {
        RegisterOrgRequest reg = new RegisterOrgRequest(
                "Acme Health",
                "acme-health",
                "Sarah Connor",
                "sarah@acme.com",
                "Password123!"
        );

        MvcResult result = mockMvc.perform(post("/api/orgs/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.activeOrgId").value("org_acme-health"))
                .andExpect(jsonPath("$.activeOrgRole").value("OWNER"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = root.path("token").asText();
        assertNotNull(token);
        assertFalse(token.isBlank());

        // Verify database state
        assertTrue(organizationRepository.existsById("org_acme-health"));
        assertTrue(userRepository.existsByEmail("sarah@acme.com"));
    }

    @Test
    void testTeamInvitationAndAcceptance() throws Exception {
        // 1. Register Org 1
        RegisterOrgRequest reg = new RegisterOrgRequest(
                "Health First",
                "health-first",
                "Admin John",
                "admin@healthfirst.com",
                "Password123!"
        );

        MvcResult regResult = mockMvc.perform(post("/api/orgs/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk())
                .andReturn();

        String adminToken = objectMapper.readTree(regResult.getResponse().getContentAsString()).path("token").asText();

        // 2. Admin invites a colleague
        InviteMemberRequest inviteReq = new InviteMemberRequest("bob@healthfirst.com", "MEMBER");
        MvcResult inviteResult = mockMvc.perform(post("/api/orgs/org_health-first/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String inviteToken = objectMapper.readTree(inviteResult.getResponse().getContentAsString())
                .path("data").path("token").asText();
        assertNotNull(inviteToken);

        // 3. Admin checks pending invitations
        mockMvc.perform(get("/api/orgs/org_health-first/invitations")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.email == 'bob@healthfirst.com')].status").value("PENDING"));

        // 4. Public frontend preview of invitation info
        mockMvc.perform(get("/api/orgs/invitations/info?token=" + inviteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("bob@healthfirst.com"))
                .andExpect(jsonPath("$.data.organizationName").value("Health First"));

        // 5. Colleague accepts invitation as new user with password
        AcceptInviteRequest acceptReq = new AcceptInviteRequest(inviteToken, "Bob Taylor", "BobPassword123!");
        MvcResult acceptResult = mockMvc.perform(post("/api/orgs/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.activeOrgId").value("org_health-first"))
                .andExpect(jsonPath("$.activeOrgRole").value("MEMBER"))
                .andReturn();

        String bobToken = objectMapper.readTree(acceptResult.getResponse().getContentAsString()).path("token").asText();
        assertNotNull(bobToken);

        // 6. Admin checks invitation list again -> status should now be ACCEPTED
        mockMvc.perform(get("/api/orgs/org_health-first/invitations")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.email == 'bob@healthfirst.com')].status").value("ACCEPTED"));

        // 7. CRITICAL TEST: Bob logs in independently with his email and password!
        LoginRequest loginReq = new LoginRequest("bob@healthfirst.com", "BobPassword123!");
        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.activeOrgId").value("org_health-first"))
                .andExpect(jsonPath("$.activeOrgRole").value("MEMBER"));

        // 8. Bob lists members of org_health-first
        mockMvc.perform(get("/api/orgs/org_health-first/members")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void testMultiOrgMembershipAndActiveContextSwitching() throws Exception {
        // Register Org 1 with Alice
        RegisterOrgRequest reg1 = new RegisterOrgRequest("Acme 1", "acme-1", "Alice", "alice@test.com", "Password123!");
        MvcResult res1 = mockMvc.perform(post("/api/orgs/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg1)))
                .andExpect(status().isOk()).andReturn();
        String aliceTokenOrg1 = objectMapper.readTree(res1.getResponse().getContentAsString()).path("token").asText();

        // Register Org 2 with Bob
        RegisterOrgRequest reg2 = new RegisterOrgRequest("Stark 2", "stark-2", "Bob", "bob@test.com", "Password123!");
        MvcResult res2 = mockMvc.perform(post("/api/orgs/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg2)))
                .andExpect(status().isOk()).andReturn();
        String bobToken = objectMapper.readTree(res2.getResponse().getContentAsString()).path("token").asText();

        // Bob in Org 2 invites Alice
        InviteMemberRequest inviteReq = new InviteMemberRequest("alice@test.com", "MEMBER");
        MvcResult inviteRes = mockMvc.perform(post("/api/orgs/org_stark-2/invitations")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteReq)))
                .andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(inviteRes.getResponse().getContentAsString()).path("data").path("token").asText();

        // Alice accepts invitation while authenticated
        AcceptInviteRequest acceptReq = new AcceptInviteRequest(token, null, null);
        mockMvc.perform(post("/api/orgs/invitations/accept")
                        .header("Authorization", "Bearer " + aliceTokenOrg1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Alice lists her organizations -> should have both acme-1 and stark-2
        mockMvc.perform(get("/api/orgs/my-orgs")
                        .header("Authorization", "Bearer " + aliceTokenOrg1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // Alice switches to Org 2
        SwitchOrgRequest switchReq = new SwitchOrgRequest("org_stark-2");
        MvcResult switchRes = mockMvc.perform(post("/api/orgs/switch")
                        .header("Authorization", "Bearer " + aliceTokenOrg1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(switchReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeOrgId").value("org_stark-2"))
                .andExpect(jsonPath("$.activeOrgRole").value("MEMBER"))
                .andReturn();

        String aliceTokenOrg2 = objectMapper.readTree(switchRes.getResponse().getContentAsString()).path("token").asText();
        assertNotNull(aliceTokenOrg2);
    }

    @Test
    void testSalesforceConnectionAndRBACRestrictions() throws Exception {
        // 1. Register Org with Admin
        RegisterOrgRequest reg = new RegisterOrgRequest("Cloud Corp", "cloud-corp", "Admin Dave", "dave@cloud.com", "Password123!");
        MvcResult res = mockMvc.perform(post("/api/orgs/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk()).andReturn();
        String adminToken = objectMapper.readTree(res.getResponse().getContentAsString()).path("token").asText();

        // 2. Admin connects Salesforce
        Map<String, Object> sfCreds = Map.of(
                "mode", "mock",
                "instanceUrl", "https://mock.salesforce.local"
        );
        mockMvc.perform(post("/api/orgs/org_cloud-corp/salesforce/connect")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sfCreds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salesforceConnected").value(true));

        // 3. Invite a regular member
        InviteMemberRequest invite = new InviteMemberRequest("member@cloud.com", "MEMBER");
        MvcResult invRes = mockMvc.perform(post("/api/orgs/org_cloud-corp/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invite)))
                .andExpect(status().isOk()).andReturn();
        String inviteToken = objectMapper.readTree(invRes.getResponse().getContentAsString()).path("data").path("token").asText();

        // Member accepts
        AcceptInviteRequest accept = new AcceptInviteRequest(inviteToken, "Member Mike", "Pass123456!");
        MvcResult accRes = mockMvc.perform(post("/api/orgs/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accept)))
                .andExpect(status().isOk()).andReturn();
        String memberToken = objectMapper.readTree(accRes.getResponse().getContentAsString()).path("token").asText();

        // 4. Regular member attempts to disconnect Salesforce -> should fail with 400
        mockMvc.perform(post("/api/orgs/org_cloud-corp/salesforce/disconnect")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // 5. Admin disconnects Salesforce -> succeeds
        mockMvc.perform(post("/api/orgs/org_cloud-corp/salesforce/disconnect")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salesforceConnected").value(false));
    }

    @Test
    void testCrossTenantDataIsolation() throws Exception {
        // Register Org 1
        RegisterOrgRequest reg1 = new RegisterOrgRequest("Acme Clinic", "acme-clinic", "Dr. A", "dr.a@acme.com", "Pass123456!");
        MvcResult res1 = mockMvc.perform(post("/api/orgs/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg1)))
                .andExpect(status().isOk()).andReturn();
        String tokenOrg1 = objectMapper.readTree(res1.getResponse().getContentAsString()).path("token").asText();

        // Register Org 2
        RegisterOrgRequest reg2 = new RegisterOrgRequest("Wayne Enterprises", "wayne-ent", "Bruce", "bruce@wayne.com", "Pass123456!");
        MvcResult res2 = mockMvc.perform(post("/api/orgs/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg2)))
                .andExpect(status().isOk()).andReturn();
        String tokenOrg2 = objectMapper.readTree(res2.getResponse().getContentAsString()).path("token").asText();

        // Dr. A creates an Account under Org 1
        OrganizationContext.setCurrentOrganization("org_acme-clinic");
        try {
            AccountEntity acc1 = new AccountEntity();
            acc1.setId("001_acme_pediatric");
            acc1.setName("Acme Pediatric Wing");
            acc1.setIndustry("Healthcare");
            acc1.setPhone("555-0100");
            acc1.markCreatedByCustomApp("dr.a@acme.com");
            acc1.setOrganizationId("org_acme-clinic");
            accountRepository.save(acc1);
        } finally {
            OrganizationContext.clear();
        }

        // Bruce creates an Account under Org 2
        OrganizationContext.setCurrentOrganization("org_wayne-ent");
        try {
            AccountEntity acc2 = new AccountEntity();
            acc2.setId("001_wayne_applied");
            acc2.setName("Wayne Applied Sciences");
            acc2.setIndustry("Defense");
            acc2.setPhone("555-0200");
            acc2.markCreatedByCustomApp("bruce@wayne.com");
            acc2.setOrganizationId("org_wayne-ent");
            accountRepository.save(acc2);
        } finally {
            OrganizationContext.clear();
        }

        // Dr. A queries accounts under Org 1 -> sees Acme Pediatric Wing, does NOT see Wayne
        mockMvc.perform(get("/api/data/Account")
                        .header("Authorization", "Bearer " + tokenOrg1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.Name == 'Acme Pediatric Wing')]").exists())
                .andExpect(jsonPath("$.data.records[?(@.Name == 'Wayne Applied Sciences')]").doesNotExist());

        // Bruce queries accounts under Org 2 -> sees Wayne Applied Sciences, does NOT see Acme
        mockMvc.perform(get("/api/data/Account")
                        .header("Authorization", "Bearer " + tokenOrg2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.Name == 'Wayne Applied Sciences')]").exists())
                .andExpect(jsonPath("$.data.records[?(@.Name == 'Acme Pediatric Wing')]").doesNotExist());
    }

    @Test
    void testReadOnlyUserCanSyncButCannotEditOrCreate() throws Exception {
        // 1. Register Org with Admin/Owner
        RegisterOrgRequest reg = new RegisterOrgRequest("Audit Global", "audit-global", "Auditor Lead", "lead@audit.org", "LeadPassword123!");
        MvcResult regRes = mockMvc.perform(post("/api/orgs/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk()).andReturn();
        String ownerToken = objectMapper.readTree(regRes.getResponse().getContentAsString()).path("token").asText();
        String orgId = "org_audit-global";

        // 2. Owner invites a READONLY member
        InviteMemberRequest invite = new InviteMemberRequest("viewer@audit.org", "READONLY");
        MvcResult inviteRes = mockMvc.perform(post("/api/orgs/" + orgId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invite)))
                .andExpect(status().isOk()).andReturn();
        String inviteToken = objectMapper.readTree(inviteRes.getResponse().getContentAsString()).path("data").path("token").asText();

        // 3. READONLY user accepts invite
        AcceptInviteRequest accept = new AcceptInviteRequest(inviteToken, "Viewer User", "ViewerPassword123!");
        MvcResult acceptRes = mockMvc.perform(post("/api/orgs/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accept)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeOrgRole").value("READONLY"))
                .andReturn();
        String viewerToken = objectMapper.readTree(acceptRes.getResponse().getContentAsString()).path("token").asText();

        // 4. READONLY user CAN trigger sync
        mockMvc.perform(post("/api/sync/run")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objects\": [\"Account\"], \"mode\": \"incremental\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 5. READONLY user CAN query records
        mockMvc.perform(get("/api/data/Account")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 6. READONLY user CANNOT create records (403 Forbidden)
        mockMvc.perform(post("/api/data/Account/create")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"Name\": \"Illegal Account Creation\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Permission Denied: Users with READONLY role cannot create records."));

        // 7. READONLY user CANNOT edit records (403 Forbidden)
        mockMvc.perform(put("/api/data/Account/ACC_123")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"Name\": \"Illegal Account Edit\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Permission Denied: Users with READONLY role cannot edit records."));
    }

    @Test
    void testSetDefaultOrganizationAndLoginSelection() throws Exception {
        // 1. Register First Organization
        RegisterOrgRequest reg1 = new RegisterOrgRequest("First Workspace", "first-workspace", "Workspace Admin", "multi@workspace.com", "Pass123456!");
        MvcResult res1 = mockMvc.perform(post("/api/orgs/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg1)))
                .andExpect(status().isOk()).andReturn();
        String token1 = objectMapper.readTree(res1.getResponse().getContentAsString()).path("token").asText();

        // 2. Create Second Organization under same user
        CreateOrgRequest createOrg2 = new CreateOrgRequest("Second Workspace", "second-workspace");
        MvcResult res2 = mockMvc.perform(post("/api/orgs")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrg2)))
                .andExpect(status().isOk()).andReturn();
        String orgId2 = objectMapper.readTree(res2.getResponse().getContentAsString()).path("data").path("id").asText();

        // 3. Mark Second Workspace as Default Workspace
        mockMvc.perform(post("/api/orgs/" + orgId2 + "/default")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.defaultOrganizationId").value(orgId2))
                .andExpect(jsonPath("$.data.defaultOrg.default").value(true));

        // 4. Verify /api/users/me returns defaultOrganizationId
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultOrganizationId").value(orgId2));

        // 5. User logs in again -> Second Workspace MUST be selected as activeOrgId!
        LoginRequest loginReq = new LoginRequest("multi@workspace.com", "Pass123456!");
        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeOrgId").value(orgId2))
                .andExpect(jsonPath("$.defaultOrgId").value(orgId2))
                .andExpect(jsonPath("$.organizations[?(@.id == '" + orgId2 + "')].default").value(true));
    }
}
