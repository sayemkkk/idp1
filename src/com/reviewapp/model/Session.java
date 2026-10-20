package com.reviewapp.model;

import java.time.Instant;

/**
 * A logged-in session. The id is an opaque random token stored in a cookie;
 * only the owning user id, role and expiry are kept server-side.
 */
public record Session(String id, long userId, Role role, Instant expiresAt) {

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
