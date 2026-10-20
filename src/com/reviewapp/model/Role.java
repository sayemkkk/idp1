package com.reviewapp.model;

/** Domain roles. Persisted in the users.role column as lowercase text. */
public enum Role {
    SUPER_ADMIN,
    HOD;

    public static Role fromDb(String value) {
        return Role.valueOf(value.trim().toUpperCase());
    }

    public String toDb() {
        return name().toLowerCase();
    }
}
