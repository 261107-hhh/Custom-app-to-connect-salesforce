package com.salesforce.sync.controller;

import com.salesforce.sync.model.entity.SyncHistoryEntity;
import com.salesforce.sync.model.entity.SyncStateEntity;
import com.salesforce.sync.repository.SyncHistoryRepository;
import com.salesforce.sync.repository.SyncStateRepository;
import com.salesforce.sync.security.SecurityUtils;
import com.salesforce.sync.service.SalesforceSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sync")
@Tag(name = "Sync Orchestration", description = "Trigger and monitor synchronization jobs between Salesforce and PostgreSQL")
public class SyncController {

    private final SalesforceSyncService syncService;
    private final SyncHistoryRepository historyRepo;
    private final SyncStateRepository stateRepo;

    public SyncController(SalesforceSyncService syncService,
                          SyncHistoryRepository historyRepo,
                          SyncStateRepository stateRepo) {
        this.syncService = syncService;
        this.historyRepo = historyRepo;
        this.stateRepo = stateRepo;
    }

    @PostMapping("/run")
    @Operation(summary = "Start Sync Process", description = "Triggers full or incremental synchronization for selected Salesforce objects on behalf of authenticated user.")
    public ResponseEntity<?> runSync(@RequestBody Map<String, Object> body, Authentication authentication) {
        try {
            String userEmail = SecurityUtils.resolveUserEmail(authentication);
            if (userEmail == null || userEmail.isBlank()) {
                userEmail = "anonymous@app.local";
            }

            @SuppressWarnings("unchecked")
            List<String> objects = (List<String>) body.getOrDefault("objects", List.of("Account", "Contact", "Opportunity", "Lead"));
            String mode = (String) body.getOrDefault("mode", "incremental");
            @SuppressWarnings("unchecked")
            Map<String, Object> filters = (Map<String, Object>) body.getOrDefault("filters", Map.of());

            Map<String, Object> result = syncService.runSync(objects, mode, filters, userEmail);
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    @Operation(summary = "Current Sync Status", description = "Returns active sync job status, progress percentage, and live execution logs.")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of("success", true, "data", syncService.getStatus()));
    }

    @GetMapping("/history")
    @Operation(summary = "Sync Audit History", description = "Returns the last 50 sync execution logs for the authenticated user.")
    public ResponseEntity<?> getHistory(Authentication authentication) {
        String userEmail = SecurityUtils.resolveUserEmail(authentication);
        List<SyncHistoryEntity> history;
        if (userEmail != null && !userEmail.isBlank()) {
            history = historyRepo.findTop50ByUserEmailOrderByIdDesc(userEmail);
        } else {
            history = historyRepo.findTop50ByOrderByIdDesc();
        }
        return ResponseEntity.ok(Map.of("success", true, "data", history));
    }

    @GetMapping("/states")
    @Operation(summary = "Object Sync States", description = "Returns current sync timestamps and record counts per Salesforce object for authenticated user.")
    public ResponseEntity<?> getStates(Authentication authentication) {
        String userEmail = SecurityUtils.resolveUserEmail(authentication);
        List<SyncStateEntity> states = stateRepo.findAllByOrderByUpdatedAtDesc();
        if (userEmail != null && !userEmail.isBlank()) {
            for (SyncStateEntity s : states) {
                s.setTotalRecords(syncService.getObjectCountByUser(s.getObjectName(), userEmail));
            }
        }
        return ResponseEntity.ok(Map.of("success", true, "data", states));
    }
}
