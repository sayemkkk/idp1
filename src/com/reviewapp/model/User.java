package com.reviewapp.model;

import java.time.Instant;

/**
 * A user account that may log in. Only SUPER_ADMIN and HOD accounts exist;
 * students never become users because feedback is anonymous.
 */
public record User(
        long id,
        String username,
        String passwordHash,
        Role role,
        String department,
        String fullName,
        boolean active,
        Instant createdAt) {

    /** Safe projection for API responses that omits the password hash. */
    public java.util.Map<String, Object> toPublicMap() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", id);
        m.put("username", username);
        m.put("role", role.name());
        m.put("department", department);
        m.put("fullName", fullName);
        m.put("active", active);
        m.put("createdAt", createdAt == null ? null : createdAt.toString());
        return m;
    }
}
