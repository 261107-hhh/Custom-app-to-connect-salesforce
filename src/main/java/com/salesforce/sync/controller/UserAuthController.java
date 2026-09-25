package com.salesforce.sync.controller;

import com.salesforce.sync.model.dto.AuthResponse;
import com.salesforce.sync.model.dto.LoginRequest;
import com.salesforce.sync.model.dto.RegisterRequest;
import com.salesforce.sync.model.entity.UserEntity;
import com.salesforce.sync.repository.UserRepository;
import com.salesforce.sync.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User Authentication", description = "Email and Password authentication for Custom App users")
public class UserAuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserAuthController(UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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

        userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail(), user.getName());
        return ResponseEntity.ok(AuthResponse.success(token, user.getEmail(), user.getName(), "Registration successful!"));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with Email and Password", description = "Authenticates user credentials and returns a JWT token.")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        if (request.getEmail() == null || request.getPassword() == null) {
            return ResponseEntity.badRequest().body(AuthResponse.error("Email and password are required."));
        }

        Optional<UserEntity> userOpt = userRepository.findByEmail(request.getEmail().trim().toLowerCase());
        if (userOpt.isEmpty() || !passwordEncoder.matches(request.getPassword(), userOpt.get().getPassword())) {
            return ResponseEntity.status(401).body(AuthResponse.error("Invalid email or password."));
        }

        UserEntity user = userOpt.get();
        String token = jwtService.generateToken(user.getEmail(), user.getName());
        return ResponseEntity.ok(AuthResponse.success(token, user.getEmail(), user.getName(), "Login successful!"));
    }

    @GetMapping("/me")
    @Operation(summary = "Current Logged-in User", description = "Returns details of the currently authenticated user.")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserEntity user)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "id", user.getId(),
                        "email", user.getEmail(),
                        "name", user.getName(),
                        "createdAt", user.getCreatedAt()
                )
        ));
    }
}
