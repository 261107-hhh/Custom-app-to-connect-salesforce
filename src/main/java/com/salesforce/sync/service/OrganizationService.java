package com.salesforce.sync.service;

import com.salesforce.sync.model.dto.*;
import com.salesforce.sync.model.entity.*;
import com.salesforce.sync.multitenancy.OrganizationContext;
import com.salesforce.sync.repository.*;
import com.salesforce.sync.security.EncryptionService;
import com.salesforce.sync.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrganizationService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository orgRepo;
    private final OrganizationMemberRepository memberRepo;
    private final OrganizationInvitationRepository invitationRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EncryptionService encryptionService;

    public OrganizationService(OrganizationRepository orgRepo,
                               OrganizationMemberRepository memberRepo,
                               OrganizationInvitationRepository invitationRepo,
                               UserRepository userRepo,
                               PasswordEncoder passwordEncoder,
                               JwtService jwtService,
                               EncryptionService encryptionService) {
        this.orgRepo = orgRepo;
        this.memberRepo = memberRepo;
        this.invitationRepo = invitationRepo;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.encryptionService = encryptionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initDefaultOrganization() {
        if (!orgRepo.existsById(OrganizationContext.DEFAULT_ORGANIZATION_ID)) {
            log.info("Initializing default organization: {}", OrganizationContext.DEFAULT_ORGANIZATION_ID);
            OrganizationEntity defaultOrg = new OrganizationEntity(
                    OrganizationContext.DEFAULT_ORGANIZATION_ID,
                    "Default Organization",
                    "default"
            );
            defaultOrg.setSfAuthMode("mock");
            defaultOrg.setSfInstanceUrl("https://mock.salesforce.local");
            orgRepo.save(defaultOrg);

            // Link existing users to default organization if any
            List<UserEntity> users = userRepo.findAll();
            for (UserEntity u : users) {
                if (!memberRepo.existsByUserIdAndOrganizationId(u.getId(), defaultOrg.getId())) {
                    memberRepo.save(new OrganizationMemberEntity(u, defaultOrg, "ADMIN"));
                }
            }
        }
    }

    @Transactional
    public AuthResponse registerOrganization(RegisterOrgRequest request) {
        if (request.getOrganizationName() == null || request.getOrganizationName().isBlank()) {
            return AuthResponse.error("Organization name is required.");
        }
        if (request.getSlug() == null || request.getSlug().isBlank()) {
            return AuthResponse.error("Organization slug is required.");
        }
        if (request.getAdminEmail() == null || request.getAdminEmail().isBlank()) {
            return AuthResponse.error("Admin email is required.");
        }
        if (request.getAdminPassword() == null || request.getAdminPassword().length() < 6) {
            return AuthResponse.error("Admin password must be at least 6 characters.");
        }

        String slug = request.getSlug().trim().toLowerCase().replaceAll("[^a-z0-9-]", "-");
        if (orgRepo.existsBySlug(slug)) {
            return AuthResponse.error("Organization slug '" + slug + "' is already taken.");
        }

        String email = request.getAdminEmail().trim().toLowerCase();
        UserEntity user = userRepo.findByEmail(email).orElseGet(() -> {
            String name = (request.getAdminName() != null && !request.getAdminName().isBlank())
                    ? request.getAdminName().trim()
                    : email.split("@")[0];
            UserEntity newUser = new UserEntity(email, passwordEncoder.encode(request.getAdminPassword()), name);
            return userRepo.save(newUser);
        });

        String orgId = "org_" + slug;
        OrganizationEntity org = new OrganizationEntity(orgId, request.getOrganizationName().trim(), slug);
        org = orgRepo.save(org);

        OrganizationMemberEntity member = new OrganizationMemberEntity(user, org, "OWNER");
        memberRepo.save(member);

        String token = jwtService.generateToken(user.getEmail(), user.getName(), org.getId(), "OWNER");
        OrganizationContext.setCurrentOrganization(org.getId());

        AuthResponse resp = AuthResponse.success(token, user.getEmail(), user.getName(), "Organization registered successfully!");
        resp.setActiveOrgId(org.getId());
        resp.setActiveOrgName(org.getName());
        resp.setActiveOrgRole("OWNER");
        resp.setOrganizations(getUserOrganizations(user));
        return resp;
    }

    @Transactional
    public OrgDetailDto createOrganization(UserEntity currentUser, CreateOrgRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Organization name is required.");
        }
        if (request.getSlug() == null || request.getSlug().isBlank()) {
            throw new IllegalArgumentException("Organization slug is required.");
        }

        String slug = request.getSlug().trim().toLowerCase().replaceAll("[^a-z0-9-]", "-");
        if (orgRepo.existsBySlug(slug)) {
            throw new IllegalArgumentException("Organization slug '" + slug + "' is already taken.");
        }

        String orgId = "org_" + slug;
        OrganizationEntity org = new OrganizationEntity(orgId, request.getName().trim(), slug);
        org = orgRepo.save(org);

        OrganizationMemberEntity member = new OrganizationMemberEntity(currentUser, org, "OWNER");
        memberRepo.save(member);

        return mapToDetailDto(org, "OWNER");
    }

    @Transactional(readOnly = true)
    public List<OrgSummaryDto> getUserOrganizations(UserEntity currentUser) {
        List<OrganizationMemberEntity> members = memberRepo.findByUserId(currentUser.getId());
        return members.stream().map(m -> {
            OrganizationEntity org = m.getOrganization();
            return new OrgSummaryDto(
                    org.getId(),
                    org.getName(),
                    org.getSlug(),
                    m.getRole(),
                    org.getStatus(),
                    org.isSalesforceConfigured(),
                    org.getSfAuthMode(),
                    org.getSfInstanceUrl()
            );
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AuthResponse switchOrganization(UserEntity currentUser, String targetOrgId) {
        OrganizationMemberEntity member = memberRepo.findByUserIdAndOrganizationId(currentUser.getId(), targetOrgId)
                .orElseThrow(() -> new IllegalArgumentException("You are not a member of organization: " + targetOrgId));

        OrganizationEntity org = member.getOrganization();
        String token = jwtService.generateToken(currentUser.getEmail(), currentUser.getName(), org.getId(), member.getRole());
        OrganizationContext.setCurrentOrganization(org.getId());

        AuthResponse resp = AuthResponse.success(token, currentUser.getEmail(), currentUser.getName(), "Switched to " + org.getName());
        resp.setActiveOrgId(org.getId());
        resp.setActiveOrgName(org.getName());
        resp.setActiveOrgRole(member.getRole());
        resp.setOrganizations(getUserOrganizations(currentUser));
        return resp;
    }

    @Transactional(readOnly = true)
    public OrgDetailDto getOrganization(String orgId, UserEntity currentUser) {
        OrganizationMemberEntity member = memberRepo.findByUserIdAndOrganizationId(currentUser.getId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Access denied: Not a member of organization " + orgId));

        return mapToDetailDto(member.getOrganization(), member.getRole());
    }

    @Transactional
    public OrgDetailDto connectSalesforce(String orgId, Map<String, Object> credentials, UserEntity currentUser) {
        OrganizationMemberEntity member = memberRepo.findByUserIdAndOrganizationId(currentUser.getId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Access denied: Not a member of organization " + orgId));

        if (!member.isAdminOrOwner()) {
            throw new IllegalArgumentException("Only Admins and Owners can configure Salesforce integration.");
        }

        OrganizationEntity org = member.getOrganization();
        String mode = (String) credentials.getOrDefault("mode", "mock");
        org.setSfAuthMode(mode);

        if ("mock".equalsIgnoreCase(mode)) {
            org.setSfInstanceUrl("https://mock.salesforce.local");
            org.setSfUsername("developer@sandbox.mock");
        } else if ("eca".equalsIgnoreCase(mode)) {
            org.setSfInstanceUrl((String) credentials.get("instanceUrl"));
            org.setSfClientId((String) credentials.get("clientId"));
            if (credentials.containsKey("clientSecret")) {
                org.setSfClientSecretEncrypted(encryptionService.encrypt((String) credentials.get("clientSecret")));
            }
        } else if ("password".equalsIgnoreCase(mode)) {
            org.setSfInstanceUrl((String) credentials.get("instanceUrl"));
            org.setSfUsername((String) credentials.get("username"));
            if (credentials.containsKey("password")) {
                org.setSfPasswordEncrypted(encryptionService.encrypt((String) credentials.get("password")));
            }
            if (credentials.containsKey("securityToken")) {
                org.setSfSecurityTokenEncrypted(encryptionService.encrypt((String) credentials.get("securityToken")));
            }
        }
        org.setUpdatedAt(LocalDateTime.now());
        org = orgRepo.save(org);

        return mapToDetailDto(org, member.getRole());
    }

    @Transactional
    public OrgDetailDto disconnectSalesforce(String orgId, UserEntity currentUser) {
        OrganizationMemberEntity member = memberRepo.findByUserIdAndOrganizationId(currentUser.getId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Access denied: Not a member of organization " + orgId));

        if (!member.isAdminOrOwner()) {
            throw new IllegalArgumentException("Only Admins and Owners can disconnect Salesforce integration.");
        }

        OrganizationEntity org = member.getOrganization();
        org.setSfAuthMode("disconnected");
        org.setSfInstanceUrl(null);
        org.setSfClientId(null);
        org.setSfClientSecretEncrypted(null);
        org.setSfUsername(null);
        org.setSfPasswordEncrypted(null);
        org.setSfSecurityTokenEncrypted(null);
        org.setUpdatedAt(LocalDateTime.now());
        org = orgRepo.save(org);

        return mapToDetailDto(org, member.getRole());
    }

    @Transactional(readOnly = true)
    public List<MemberDto> getMembers(String orgId, UserEntity currentUser) {
        memberRepo.findByUserIdAndOrganizationId(currentUser.getId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Access denied: Not a member of organization " + orgId));

        List<OrganizationMemberEntity> members = memberRepo.findByOrganizationId(orgId);
        return members.stream().map(m -> new MemberDto(
                m.getId(),
                m.getUser().getId(),
                m.getUser().getEmail(),
                m.getUser().getName(),
                m.getRole(),
                m.getStatus(),
                m.getJoinedAt()
        )).collect(Collectors.toList());
    }

    @Transactional
    public InvitationDto inviteMember(String orgId, InviteMemberRequest request, UserEntity currentUser) {
        OrganizationMemberEntity member = memberRepo.findByUserIdAndOrganizationId(currentUser.getId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Access denied: Not a member of organization " + orgId));

        if (!member.isAdminOrOwner()) {
            throw new IllegalArgumentException("Only Admins and Owners can invite new members.");
        }

        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Invitee email is required.");
        }

        String email = request.getEmail().trim().toLowerCase();
        OrganizationEntity org = member.getOrganization();

        // Check if already a member
        Optional<UserEntity> existingUser = userRepo.findByEmail(email);
        if (existingUser.isPresent() && memberRepo.existsByUserIdAndOrganizationId(existingUser.get().getId(), orgId)) {
            throw new IllegalArgumentException("User " + email + " is already a member of this organization.");
        }

        String token = "inv_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        String role = (request.getRole() != null && !request.getRole().isBlank()) ? request.getRole().toUpperCase() : "MEMBER";

        OrganizationInvitationEntity invitation = new OrganizationInvitationEntity(
                org, email, role, token, expiresAt, currentUser
        );
        invitation = invitationRepo.save(invitation);

        return new InvitationDto(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getRole(),
                invitation.getToken(),
                invitation.getExpiresAt(),
                invitation.getStatus(),
                currentUser.getEmail(),
                invitation.getCreatedAt()
        );
    }

    @Transactional
    public AuthResponse acceptInvitation(AcceptInviteRequest request, UserEntity currentUserOrNull) {
        if (request.getToken() == null || request.getToken().isBlank()) {
            return AuthResponse.error("Invitation token is required.");
        }

        OrganizationInvitationEntity invitation = invitationRepo.findByToken(request.getToken().trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation token."));

        if (!"PENDING".equalsIgnoreCase(invitation.getStatus())) {
            return AuthResponse.error("Invitation has already been " + invitation.getStatus().toLowerCase());
        }

        if (invitation.isExpired()) {
            invitation.setStatus("EXPIRED");
            invitationRepo.save(invitation);
            return AuthResponse.error("Invitation has expired.");
        }

        UserEntity user = currentUserOrNull;
        if (user == null) {
            String inviteEmail = (invitation.getEmail() != null) ? invitation.getEmail().trim().toLowerCase() : "";
            Optional<UserEntity> existing = userRepo.findByEmail(inviteEmail);
            if (existing.isPresent()) {
                user = existing.get();
                // Ensure the password provided on acceptance is encoded and saved as their active login password
                if (request.getPassword() != null && !request.getPassword().isBlank()) {
                    if (request.getPassword().length() < 6) {
                        return AuthResponse.error("Password must be at least 6 characters.");
                    }
                    user.setPassword(passwordEncoder.encode(request.getPassword()));
                }
                if (request.getName() != null && !request.getName().isBlank()) {
                    user.setName(request.getName().trim());
                }
                user = userRepo.save(user);
            } else {
                if (request.getPassword() == null || request.getPassword().length() < 6) {
                    return AuthResponse.error("Password must be at least 6 characters for new account.");
                }
                String name = (request.getName() != null && !request.getName().isBlank())
                        ? request.getName().trim()
                        : inviteEmail.split("@")[0];
                user = new UserEntity(inviteEmail, passwordEncoder.encode(request.getPassword()), name);
                user = userRepo.save(user);
            }
        } else {
            // Already authenticated user updating their password optionally
            if (request.getPassword() != null && !request.getPassword().isBlank() && request.getPassword().length() >= 6) {
                user.setPassword(passwordEncoder.encode(request.getPassword()));
                user = userRepo.save(user);
            }
        }

        OrganizationEntity org = invitation.getOrganization();
        if (!memberRepo.existsByUserIdAndOrganizationId(user.getId(), org.getId())) {
            OrganizationMemberEntity member = new OrganizationMemberEntity(user, org, invitation.getRole());
            memberRepo.save(member);
        }

        invitation.setStatus("ACCEPTED");
        invitationRepo.save(invitation);

        String token = jwtService.generateToken(user.getEmail(), user.getName(), org.getId(), invitation.getRole());
        OrganizationContext.setCurrentOrganization(org.getId());

        AuthResponse resp = AuthResponse.success(token, user.getEmail(), user.getName(), "Joined " + org.getName() + " successfully!");
        resp.setActiveOrgId(org.getId());
        resp.setActiveOrgName(org.getName());
        resp.setActiveOrgRole(invitation.getRole());
        resp.setOrganizations(getUserOrganizations(user));
        return resp;
    }

    @Transactional(readOnly = true)
    public List<InvitationDto> getInvitations(String orgId, UserEntity currentUser) {
        OrganizationMemberEntity member = memberRepo.findByUserIdAndOrganizationId(currentUser.getId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Access denied: Not a member of organization " + orgId));

        if (!member.isAdminOrOwner()) {
            throw new IllegalArgumentException("Only Admins and Owners can view sent invitations.");
        }

        List<OrganizationInvitationEntity> invitations = invitationRepo.findByOrganizationId(orgId);
        return invitations.stream().map(inv -> {
            String status = inv.getStatus();
            if ("PENDING".equalsIgnoreCase(status) && inv.isExpired()) {
                status = "EXPIRED";
            }
            return new InvitationDto(
                    inv.getId(),
                    inv.getEmail(),
                    inv.getRole(),
                    inv.getToken(),
                    inv.getExpiresAt(),
                    status,
                    inv.getInvitedByUser() != null ? inv.getInvitedByUser().getEmail() : "Admin",
                    inv.getCreatedAt()
            );
        }).collect(Collectors.toList());
    }

    @Transactional
    public void revokeInvitation(String orgId, Long invitationId, UserEntity currentUser) {
        OrganizationMemberEntity member = memberRepo.findByUserIdAndOrganizationId(currentUser.getId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Access denied: Not a member of organization " + orgId));

        if (!member.isAdminOrOwner()) {
            throw new IllegalArgumentException("Only Admins and Owners can revoke invitations.");
        }

        OrganizationInvitationEntity invitation = invitationRepo.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found: " + invitationId));

        if (!invitation.getOrganization().getId().equals(orgId)) {
            throw new IllegalArgumentException("Invitation does not belong to organization " + orgId);
        }

        invitation.setStatus("REVOKED");
        invitationRepo.save(invitation);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getInvitationInfo(String token) {
        if (token == null || token.isBlank()) {
            return Map.of("valid", false, "error", "Invitation token is required.");
        }

        Optional<OrganizationInvitationEntity> invOpt = invitationRepo.findByToken(token.trim());
        if (invOpt.isEmpty()) {
            return Map.of("valid", false, "error", "Invalid invitation token.");
        }

        OrganizationInvitationEntity inv = invOpt.get();
        if (!"PENDING".equalsIgnoreCase(inv.getStatus())) {
            return Map.of("valid", false, "error", "Invitation has already been " + inv.getStatus().toLowerCase());
        }

        if (inv.isExpired()) {
            return Map.of("valid", false, "error", "Invitation has expired.");
        }

        return Map.of(
                "valid", true,
                "email", inv.getEmail(),
                "role", inv.getRole(),
                "organizationId", inv.getOrganization().getId(),
                "organizationName", inv.getOrganization().getName(),
                "organizationSlug", inv.getOrganization().getSlug(),
                "invitedBy", inv.getInvitedByUser() != null ? inv.getInvitedByUser().getEmail() : "Admin",
                "expiresAt", inv.getExpiresAt()
        );
    }

    @Transactional
    public MemberDto updateMemberRole(String orgId, Long targetUserId, String newRole, UserEntity currentUser) {
        OrganizationMemberEntity requester = memberRepo.findByUserIdAndOrganizationId(currentUser.getId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Access denied: Not a member of organization " + orgId));

        if (!requester.isAdminOrOwner()) {
            throw new IllegalArgumentException("Only Admins and Owners can change member roles.");
        }

        OrganizationMemberEntity target = memberRepo.findByUserIdAndOrganizationId(targetUserId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found in organization: " + targetUserId));

        target.setRole(newRole.toUpperCase());
        target = memberRepo.save(target);

        return new MemberDto(
                target.getId(),
                target.getUser().getId(),
                target.getUser().getEmail(),
                target.getUser().getName(),
                target.getRole(),
                target.getStatus(),
                target.getJoinedAt()
        );
    }

    @Transactional
    public void removeMember(String orgId, Long targetUserId, UserEntity currentUser) {
        OrganizationMemberEntity requester = memberRepo.findByUserIdAndOrganizationId(currentUser.getId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Access denied: Not a member of organization " + orgId));

        if (!requester.isAdminOrOwner()) {
            throw new IllegalArgumentException("Only Admins and Owners can remove members.");
        }

        OrganizationMemberEntity target = memberRepo.findByUserIdAndOrganizationId(targetUserId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found in organization: " + targetUserId));

        if ("OWNER".equalsIgnoreCase(target.getRole())) {
            long ownerCount = memberRepo.findByOrganizationId(orgId).stream()
                    .filter(m -> "OWNER".equalsIgnoreCase(m.getRole())).count();
            if (ownerCount <= 1) {
                throw new IllegalArgumentException("Cannot remove the sole OWNER of the organization.");
            }
        }

        memberRepo.deleteByUserIdAndOrganizationId(targetUserId, orgId);
    }

    private OrgDetailDto mapToDetailDto(OrganizationEntity org, String currentUserRole) {
        OrgDetailDto dto = new OrgDetailDto();
        dto.setId(org.getId());
        dto.setName(org.getName());
        dto.setSlug(org.getSlug());
        dto.setStatus(org.getStatus());
        dto.setSalesforceConnected(org.isSalesforceConfigured());
        dto.setSfAuthMode(org.getSfAuthMode());
        dto.setSfInstanceUrl(org.getSfInstanceUrl());
        dto.setSfUsername(org.getSfUsername());
        dto.setCurrentUserRole(currentUserRole);
        dto.setMemberCount(memberRepo.countByOrganizationId(org.getId()));
        dto.setCreatedAt(org.getCreatedAt());
        return dto;
    }
}
