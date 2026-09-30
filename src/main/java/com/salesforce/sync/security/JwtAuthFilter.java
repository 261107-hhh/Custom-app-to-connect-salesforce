package com.salesforce.sync.security;

import com.salesforce.sync.model.entity.UserEntity;
import com.salesforce.sync.multitenancy.OrganizationContext;
import com.salesforce.sync.repository.OrganizationMemberRepository;
import com.salesforce.sync.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public JwtAuthFilter(JwtService jwtService,
                         UserRepository userRepository,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) OrganizationMemberRepository memberRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this(jwtService, userRepository, null);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7).trim();
        try {
            final String userEmail = jwtService.extractEmail(jwt);
            log.info("JWT Auth: Extracted email {}", userEmail);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Optional<UserEntity> userOpt = userRepository.findByEmail(userEmail);

                if (userOpt.isPresent() && jwtService.isTokenValid(jwt, userEmail)) {
                    UserEntity user = userOpt.get();
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

                    String orgRole = jwtService.extractOrgRole(jwt);
                    if (orgRole != null && !orgRole.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + orgRole.toUpperCase()));
                    }

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            authorities
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    // Set Organization Context safely from Header (priority) or JWT claim, with fallback to user orgs
                    String targetOrgId = null;
                    String orgHeader = request.getHeader("X-Organization-ID");
                    if (OrganizationContext.isValidOrgId(orgHeader)) {
                        targetOrgId = orgHeader.trim();
                    } else {
                        String activeOrgId = jwtService.extractActiveOrgId(jwt);
                        if (OrganizationContext.isValidOrgId(activeOrgId)) {
                            targetOrgId = activeOrgId.trim();
                        } else if (OrganizationContext.isValidOrgId(user.getDefaultOrganizationId())) {
                            targetOrgId = user.getDefaultOrganizationId().trim();
                        } else if (memberRepository != null) {
                            List<com.salesforce.sync.model.entity.OrganizationMemberEntity> memberships = memberRepository.findByUserId(user.getId());
                            if (!memberships.isEmpty() && memberships.get(0).getOrganization() != null) {
                                targetOrgId = memberships.get(0).getOrganization().getId();
                            }
                        }
                    }

                    if (targetOrgId != null && OrganizationContext.isValidOrgId(targetOrgId)) {
                        OrganizationContext.setCurrentOrganization(targetOrgId);
                    } else {
                        OrganizationContext.setCurrentOrganization(OrganizationContext.DEFAULT_ORGANIZATION_ID);
                    }

                    log.info("JWT Auth: Successfully authenticated user {} for org {}", userEmail, OrganizationContext.getCurrentOrganization());
                } else {
                    log.warn("JWT Auth: User not found or token invalid for {}", userEmail);
                }
            }
        } catch (Exception e) {
            log.error("JWT Auth: Exception occurred during token processing: {}", e.getMessage(), e);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
