package com.salesforce.sync.controller;

import com.salesforce.sync.model.dto.*;
import com.salesforce.sync.model.entity.UserEntity;
import com.salesforce.sync.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orgs")
@Tag(name = "Organization & Multi-Tenancy", description = "Tenant registration, organization management, member invitations, and active context switching")
public class OrganizationController {

    private final OrganizationService orgService;

    public OrganizationController(OrganizationService orgService) {
        this.orgService = orgService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register New Organization", description = "Self-service onboarding creating a new organization and root admin user.")
    public ResponseEntity<AuthResponse> registerOrganization(@RequestBody RegisterOrgRequest request) {
        AuthResponse response = orgService.registerOrganization(request);
        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Create Organization", description = "Creates a new organization for the currently authenticated user.")
    public ResponseEntity<?> createOrganization(@RequestBody CreateOrgRequest request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserEntity user)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }
        try {
            OrgDetailDto org = orgService.createOrganization(user, request);
            return ResponseEntity.ok(Map.of("success", true, "data", org));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/my-orgs")
    @Operation(summary = "List My Organizations", description = "Returns all organizations the authenticated user belongs to.")
    public ResponseEntity<?> getMyOrganizations(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserEntity user)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }
        List<OrgSummaryDto> orgs = orgService.getUserOrganizations(user);
        return ResponseEntity.ok(Map.of("success", true, "data", orgs));
    }

    @PostMapping("/switch")
    @Operation(summary = "Switch Active Organization", description = "Switches the active organization context and returns an updated JWT token.")
    public ResponseEntity<AuthResponse> switchOrganization(@RequestBody SwitchOrgRequest request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserEntity user)) {
            return ResponseEntity.status(401).body(AuthResponse.error("Unauthorized"));
        }
        try {
            AuthResponse response = orgService.switchOrganization(user, request.getOrganizationId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(AuthResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{orgId}")
    @Operation(summary = "Get Organization Details", description = "Returns detailed profile and Salesforce connection status of the organization.")
    public ResponseEntity<?> getOrganization(@PathVariable String orgId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserEntity user)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }
        try {
            OrgDetailDto org = orgService.getOrganization(orgId, user);
            return ResponseEntity.ok(Map.of("success", true, "data", org));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/{orgId}/salesforce/connect")
    @Operation(summary = "Connect Salesforce for Organization", description = "Admins/Owners authenticate their corporate Salesforce org for the whole team.")
    public ResponseEntity<?> connectSalesforce(@PathVariable String orgId,
                                              @RequestBody Map<String, Object> credentials,
                                              Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserEntity user)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }
        try {
            OrgDetailDto org = orgService.connectSalesforce(orgId, credentials, user);
            return ResponseEntity.ok(Map.of("success", true, "data", org));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/{orgId}/salesforce/disconnect")
    @Operation(summary = "Disconnect Salesforce for Organization", description = "Clears Salesforce credentials for the organization.")
    public ResponseEntity<?> disconnectSalesforce(@PathVariable String orgId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserEntity user)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }
        try {
            OrgDetailDto org = orgService.disconnectSalesforce(orgId, user);
            return ResponseEntity.ok(Map.of("success", true, "data", org));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/{orgId}/members")
    @Operation(summary = "List Organization Members", description = "Returns all members, roles, and status in the organization.")
    public ResponseEntity<?> getMembers(@PathVariable String orgId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserEntity user)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }
        try {
            List<MemberDto> members = orgService.getMembers(orgId, user);
            return ResponseEntity.ok(Map.of("success", true, "data", members));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/{orgId}/invitations")
    @Operation(summary = "Invite Team Member", description = "Admins/Owners invite a colleague by email with a specified role.")
    public ResponseEntity<?> inviteMember(@PathVariable String orgId,
                                         @RequestBody InviteMemberRequest request,
                                         Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserEntity user)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }
        try {
            InvitationDto invitation = orgService.inviteMember(orgId, request, user);
            return ResponseEntity.ok(Map.of("success", true, "data", invitation));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/{orgId}/invitations")
    @Operation(summary = "List Organization Invitations", description = "Admins/Owners view all sent invitations and their status (PENDING, ACCEPTED, EXPIRED, REVOKED).")
    public ResponseEntity<?> getInvitations(@PathVariable String orgId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserEntity user)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }
        try {
            List<InvitationDto> invitations = orgService.getInvitations(orgId, user);
            return ResponseEntity.ok(Map.of("success", true, "data", invitations));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/{orgId}/invitations/{invitationId}")
    @Operation(summary = "Revoke Invitation", description = "Admins/Owners cancel/revoke a pending team invitation.")
    public ResponseEntity<?> revokeInvitation(@PathVariable String orgId,
                                             @PathVariable Long invitationId,
                                             Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserEntity user)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }
        try {
            orgService.revokeInvitation(orgId, invitationId, user);
            return ResponseEntity.ok(Map.of("success", true, "data", Map.of("message", "Invitation revoked successfully.")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/invitations/info")
    @Operation(summary = "Get Invitation Info", description = "Public endpoint to validate and preview invitation metadata for the frontend before acceptance.")
    public ResponseEntity<?> getInvitationInfo(@RequestParam String token) {
        Map<String, Object> info = orgService.getInvitationInfo(token);
        if (Boolean.FALSE.equals(info.get("valid"))) {
            return ResponseEntity.badRequest().body(info);
        }
        return ResponseEntity.ok(Map.of("success", true, "data", info));
    }

    @PostMapping("/invitations/accept")
    @Operation(summary = "Accept Team Invitation", description = "Accepts an invitation token to join an organization.")
    public ResponseEntity<AuthResponse> acceptInvitation(@RequestBody AcceptInviteRequest request,
                                                         Authentication authentication) {
        UserEntity currentUser = (authentication != null && authentication.getPrincipal() instanceof UserEntity u) ? u : null;
        try {
            AuthResponse response = orgService.acceptInvitation(request, currentUser);
            if (!response.isSuccess()) {
                return ResponseEntity.badRequest().body(response);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(AuthResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{orgId}/members/{userId}/role")
    @Operation(summary = "Update Member Role", description = "Admins/Owners update a team member's role (ADMIN, MEMBER, READONLY).")
    public ResponseEntity<?> updateMemberRole(@PathVariable String orgId,
                                             @PathVariable Long userId,
                                             @RequestBody UpdateMemberRoleRequest request,
                                             Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserEntity user)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }
        try {
            MemberDto member = orgService.updateMemberRole(orgId, userId, request.getRole(), user);
            return ResponseEntity.ok(Map.of("success", true, "data", member));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/{orgId}/members/{userId}")
    @Operation(summary = "Remove Member", description = "Admins/Owners remove a team member from the organization.")
    public ResponseEntity<?> removeMember(@PathVariable String orgId,
                                         @PathVariable Long userId,
                                         Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserEntity user)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }
        try {
            orgService.removeMember(orgId, userId, user);
            return ResponseEntity.ok(Map.of("success", true, "data", Map.of("message", "Member removed successfully.")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/{orgId}/default")
    @Operation(summary = "Set Default Organization", description = "Marks specified organization as user's default workspace for future logins.")
    public ResponseEntity<?> setDefaultOrgPost(@PathVariable String orgId, Authentication authentication) {
        return handleSetDefaultOrg(orgId, authentication);
    }

    @PutMapping("/{orgId}/default")
    @Operation(summary = "Set Default Organization", description = "Marks specified organization as user's default workspace for future logins.")
    public ResponseEntity<?> setDefaultOrgPut(@PathVariable String orgId, Authentication authentication) {
        return handleSetDefaultOrg(orgId, authentication);
    }

    private ResponseEntity<?> handleSetDefaultOrg(String orgId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserEntity user)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }
        try {
            OrgSummaryDto defaultOrg = orgService.setDefaultOrganization(user, orgId);
            List<OrgSummaryDto> orgs = orgService.getUserOrganizations(user);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "defaultOrganizationId", orgId,
                            "defaultOrg", defaultOrg,
                            "organizations", orgs,
                            "message", "Default workspace updated successfully to " + defaultOrg.getName()
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
