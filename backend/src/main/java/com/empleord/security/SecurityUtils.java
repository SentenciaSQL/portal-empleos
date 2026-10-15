package com.empleord.security;

import com.empleord.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserPrincipal requireUser() {
        UserPrincipal principal = currentUser();
        if (principal == null) {
            throw ApiException.unauthorized("Debes iniciar sesión.");
        }
        return principal;
    }

    public static UserPrincipal currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }
        return principal;
    }
}
