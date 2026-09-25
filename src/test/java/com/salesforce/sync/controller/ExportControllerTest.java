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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class ExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;
    private final String userEmail = "export.specialist@enterprise.io";

    @BeforeEach
    void setUp() throws Exception {
        accountRepository.deleteAll();
        userRepository.deleteAll();

        RegisterRequest reg = new RegisterRequest(userEmail, "Password123!", "Export Specialist");
        String res = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn().getResponse().getContentAsString();
        userToken = objectMapper.readTree(res).path("token").asText();

        AccountEntity acc = new AccountEntity();
        acc.setId("001exportTest001");
        acc.setName("Global Export Corp");
        acc.setBillingCity("Seattle");
        acc.setRawData("{\"Id\":\"001exportTest001\",\"Name\":\"Global Export Corp\",\"AnnualRevenue\":8000000}");
        acc.markCreatedByCustomApp(userEmail);
        accountRepository.save(acc);
    }

    @Test
    void testExportCsv() throws Exception {
        mockMvc.perform(get("/api/export/Account?format=csv")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Account_export.csv\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Id,Name,Type,Industry")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Global Export Corp")));
    }

    @Test
    void testExportJson() throws Exception {
        mockMvc.perform(get("/api/export/Account?format=json")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Account_export.json\""))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].Id").value("001exportTest001"))
                .andExpect(jsonPath("$[0].Name").value("Global Export Corp"));
    }

    @Test
    void testExportDoesNotLeakOtherUsersData() throws Exception {
        // Create an account belonging to someone else
        AccountEntity otherAcc = new AccountEntity();
        otherAcc.setId("001other001");
        otherAcc.setName("Secret Rival Corp");
        otherAcc.markCreatedByCustomApp("rival@othercompany.com");
        accountRepository.save(otherAcc);

        // Authenticated user exports records
        mockMvc.perform(get("/api/export/Account?format=json")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].Name").value("Global Export Corp"));
    }
}
