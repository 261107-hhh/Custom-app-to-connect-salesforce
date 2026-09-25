package com.salesforce.sync.security;

import com.salesforce.sync.model.entity.UserEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Resolves the email address of the currently authenticated user from SecurityContextHolder.
     * Returns null if unauthenticated or anonymous.
     */
    public static String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return resolveUserEmail(auth);
    }

    /**
     * Extracts user email from an Authentication token or principal.
     * Returns null if authentication is null, not authenticated, or anonymous.
     */
    public static String resolveUserEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserEntity user) {
            return user.getEmail();
        }
        if (principal instanceof UserDetails ud) {
            return ud.getUsername();
        }
        if (principal instanceof String str && !str.equalsIgnoreCase("anonymousUser")) {
            return str;
        }
        if (authentication.getName() != null && !authentication.getName().equalsIgnoreCase("anonymousUser")) {
            return authentication.getName();
        }
        return null;
    }
}
