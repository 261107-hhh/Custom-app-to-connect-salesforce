package com.salesforce.sync.controller;

import com.salesforce.sync.model.entity.SyncHistoryEntity;
import com.salesforce.sync.model.entity.SyncStateEntity;
import com.salesforce.sync.repository.SyncHistoryRepository;
import com.salesforce.sync.repository.SyncStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class SyncHistoryAndStateSerializationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SyncHistoryRepository historyRepository;

    @Autowired
    private SyncStateRepository stateRepository;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        stateRepository.deleteAll();

        SyncHistoryEntity h = new SyncHistoryEntity();
        h.setObjectName("Account");
        h.setSyncMode("incremental");
        h.setStatus("SUCCESS");
        h.setRecordsFetched(42);
        h.setRecordsUpserted(42);
        h.setDurationMs(150L);
        h.setStartTime(LocalDateTime.now());
        historyRepository.save(h);

        SyncStateEntity s = new SyncStateEntity("Account", "2026-09-24T12:00:00Z", "incremental", 100L, "SUCCESS");
        stateRepository.save(s);
    }

    @Test
    void testSyncHistoryReturnsSnakeCaseFieldsForDashboard() throws Exception {
        mockMvc.perform(get("/api/sync/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].object_name").value("Account"))
                .andExpect(jsonPath("$.data[0].sync_mode").value("incremental"))
                .andExpect(jsonPath("$.data[0].records_fetched").value(42))
                .andExpect(jsonPath("$.data[0].records_upserted").value(42))
                .andExpect(jsonPath("$.data[0].duration_ms").value(150));
    }

    @Test
    void testSyncStatesReturnsSnakeCaseFieldsForDashboard() throws Exception {
        mockMvc.perform(get("/api/sync/states"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].object_name").value("Account"))
                .andExpect(jsonPath("$.data[0].last_sync_timestamp").value("2026-09-24T12:00:00Z"))
                .andExpect(jsonPath("$.data[0].last_sync_mode").value("incremental"))
                .andExpect(jsonPath("$.data[0].total_records").value(100));
    }
}
