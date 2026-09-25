package com.salesforce.sync.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testSimulateRemoteUpdateAccount() throws Exception {
        mockMvc.perform(post("/api/mock/simulate-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectName\":\"Account\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.record.Id").isNotEmpty())
                .andExpect(jsonPath("$.record.Name").isNotEmpty());
    }

    @Test
    void testSimulateRemoteUpdateContact() throws Exception {
        mockMvc.perform(post("/api/mock/simulate-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectName\":\"Contact\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.record.Id").value(org.hamcrest.Matchers.startsWith("003mock")));
    }
}
