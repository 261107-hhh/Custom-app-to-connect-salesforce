package com.salesforce.sync.controller;

import com.salesforce.sync.model.dto.AuthResponse;
import com.salesforce.sync.model.dto.LoginRequest;
import com.salesforce.sync.model.dto.OrgSummaryDto;
import com.salesforce.sync.model.dto.RegisterRequest;
import com.salesforce.sync.model.entity.OrganizationEntity;
import com.salesforce.sync.model.entity.OrganizationMemberEntity;
import com.salesforce.sync.model.entity.UserEntity;
import com.salesforce.sync.multitenancy.OrganizationContext;
import com.salesforce.sync.repository.OrganizationMemberRepository;
import com.salesforce.sync.repository.OrganizationRepository;
import com.salesforce.sync.repository.UserRepository;
import com.salesforce.sync.security.JwtService;
import com.salesforce.sync.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User Authentication", description = "Email and Password authentication with multi-organization support")
public class UserAuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OrganizationService organizationService;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;

    public UserAuthController(UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              JwtService jwtService,
                              OrganizationService organizationService,
                              OrganizationRepository organizationRepository,
                              OrganizationMemberRepository memberRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.organizationService = organizationService;
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
    }

    @PostMapping("/register")
    @Operation(summary = "Register new user", description = "Creates a new user account with email and password.")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(AuthResponse.error("Email is required."));
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            return ResponseEntity.badRequest().body(AuthResponse.error("Password must be at least 6 characters."));
        }
        if (userRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            return ResponseEntity.badRequest().body(AuthResponse.error("Email is already registered."));
        }

        String displayName = (request.getName() != null && !request.getName().isBlank())
                ? request.getName().trim()
                : request.getEmail().split("@")[0];

        UserEntity user = new UserEntity(
                request.getEmail().trim().toLowerCase(),
                passwordEncoder.encode(request.getPassword()),
                displayName
        );

        user = userRepository.save(user);

        // Auto-link to default organization if it exists and user has no orgs yet
        Optional<OrganizationEntity> defaultOrgOpt = organizationRepository.findById(OrganizationContext.DEFAULT_ORGANIZATION_ID);
        String activeOrgId = OrganizationContext.DEFAULT_ORGANIZATION_ID;
        String activeOrgName = "Default Organization";
        String activeOrgRole = "MEMBER";

        if (defaultOrgOpt.isPresent()) {
            OrganizationMemberEntity member = new OrganizationMemberEntity(user, defaultOrgOpt.get(), "MEMBER");
            memberRepository.save(member);
            activeOrgId = defaultOrgOpt.get().getId();
            activeOrgName = defaultOrgOpt.get().getName();
        }

        String token = jwtService.generateToken(user.getEmail(), user.getName(), activeOrgId, activeOrgRole);
        OrganizationContext.setCurrentOrganization(activeOrgId);

        AuthResponse resp = AuthResponse.success(token, user.getEmail(), user.getName(), "Registration successful!");
        resp.setActiveOrgId(activeOrgId);
        resp.setActiveOrgName(activeOrgName);
        resp.setActiveOrgRole(activeOrgRole);
        resp.setOrganizations(organizationService.getUserOrganizations(user));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/login")
    @Operation(summary = "Login with Email and Password", description = "Authenticates user credentials and returns a JWT token with active organization context.")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        if (request.getEmail() == null || request.getPassword() == null) {
            return ResponseEntity.badRequest().body(AuthResponse.error("Email and password are required."));
        }

        Optional<UserEntity> userOpt = userRepository.findByEmail(request.getEmail().trim().toLowerCase());
        if (userOpt.isEmpty() || !passwordEncoder.matches(request.getPassword(), userOpt.get().getPassword())) {
            return ResponseEntity.status(401).body(AuthResponse.error("Invalid email or password."));
        }

        UserEntity user = userOpt.get();
        List<OrgSummaryDto> orgs = organizationService.getUserOrganizations(user);

        String activeOrgId = OrganizationContext.DEFAULT_ORGANIZATION_ID;
        String activeOrgName = "Default Organization";
        String activeOrgRole = "USER";

        if (!orgs.isEmpty()) {
            OrgSummaryDto primaryOrg = orgs.get(0);
            activeOrgId = primaryOrg.getId();
            activeOrgName = primaryOrg.getName();
            activeOrgRole = primaryOrg.getRole();
        } else {
            // Auto-link to default org if no org exists
            Optional<OrganizationEntity> defaultOrgOpt = organizationRepository.findById(OrganizationContext.DEFAULT_ORGANIZATION_ID);
            if (defaultOrgOpt.isPresent()) {
                OrganizationMemberEntity member = new OrganizationMemberEntity(user, defaultOrgOpt.get(), "MEMBER");
                memberRepository.save(member);
                orgs = organizationService.getUserOrganizations(user);
                activeOrgId = defaultOrgOpt.get().getId();
                activeOrgName = defaultOrgOpt.get().getName();
                activeOrgRole = "MEMBER";
            }
        }

        String token = jwtService.generateToken(user.getEmail(), user.getName(), activeOrgId, activeOrgRole);
        OrganizationContext.setCurrentOrganization(activeOrgId);

        AuthResponse resp = AuthResponse.success(token, user.getEmail(), user.getName(), "Login successful!");
        resp.setActiveOrgId(activeOrgId);
        resp.setActiveOrgName(activeOrgName);
        resp.setActiveOrgRole(activeOrgRole);
        resp.setOrganizations(orgs);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/me")
    @Operation(summary = "Current Logged-in User", description = "Returns details of the currently authenticated user, active organization, and organization memberships.")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserEntity user)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        List<OrgSummaryDto> orgs = organizationService.getUserOrganizations(user);
        String currentOrgId = OrganizationContext.getCurrentOrganization();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "id", user.getId(),
                        "email", user.getEmail(),
                        "name", user.getName(),
                        "createdAt", user.getCreatedAt(),
                        "activeOrganizationId", currentOrgId,
                        "organizations", orgs
                )
        ));
    }
}
