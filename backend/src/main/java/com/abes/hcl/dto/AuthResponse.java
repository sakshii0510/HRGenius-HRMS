package com.abes.hcl.dto;

import com.abes.hcl.entity.Role;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        AuthenticatedUser user) {

    public record AuthenticatedUser(Long id, String name, String email, Role role) {
    }
}
