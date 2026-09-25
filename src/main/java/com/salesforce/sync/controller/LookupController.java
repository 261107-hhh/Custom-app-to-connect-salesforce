package com.salesforce.sync.controller;

import com.salesforce.sync.repository.AccountRepository;
import com.salesforce.sync.repository.ContactRepository;
import com.salesforce.sync.repository.OpportunityRepository;
import com.salesforce.sync.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lookups")
@Tag(name = "Lookup Dropdowns", description = "Lightweight lookup options for relational selectors in UI")
public class LookupController {

    private final AccountRepository accountRepo;
    private final ContactRepository contactRepo;
    private final OpportunityRepository opportunityRepo;

    public LookupController(AccountRepository accountRepo,
                            ContactRepository contactRepo,
                            OpportunityRepository opportunityRepo) {
        this.accountRepo = accountRepo;
        this.contactRepo = contactRepo;
        this.opportunityRepo = opportunityRepo;
    }

    @GetMapping("/{objectName}")
    @Operation(summary = "Get Lookup Options", description = "Returns list of ID and Name for dropdown selects for authenticated user.")
    public ResponseEntity<?> getLookups(@PathVariable String objectName, Authentication authentication) {
        String userEmail = SecurityUtils.resolveUserEmail(authentication);
        if (userEmail == null || userEmail.isBlank()) {
            return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
        }

        String lower = objectName.toLowerCase();
        List<?> results;

        if ("account".equals(lower)) {
            results = accountRepo.findAllLookupsByUser(userEmail).stream()
                    .map(p -> Map.of("id", p.getId(), "name", p.getName() != null ? p.getName() : p.getId()))
                    .toList();
        } else if ("contact".equals(lower)) {
            results = contactRepo.findAllLookupsByUser(userEmail).stream()
                    .map(p -> Map.of("id", p.getId(), "name", p.getName() != null ? p.getName() : p.getId()))
                    .toList();
        } else if ("opportunity".equals(lower)) {
            results = opportunityRepo.findAllLookupsByUser(userEmail).stream()
                    .map(p -> Map.of("id", p.getId(), "name", p.getName() != null ? p.getName() : p.getId()))
                    .toList();
        } else {
            results = List.of();
        }

        return ResponseEntity.ok(Map.of("success", true, "data", results));
    }
}
