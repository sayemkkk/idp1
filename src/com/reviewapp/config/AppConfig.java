package com.reviewapp.config;

/**
 * Central application configuration. Values can be overridden with
 * environment variables or system properties so the same build can run in
 * different environments without recompilation.
 */
public final class AppConfig {

    /** TCP port the HTTP server listens on. */
    public static final int PORT = getInt("PORT", 8080);

    /** Path to the SQLite database file. */
    public static final String DB_PATH = get("DB_PATH", "review.db");

    /** Directory that serves static frontend assets. */
    public static final String PUBLIC_DIR = get("PUBLIC_DIR", "public");

    /** Lifetime of an authentication session, in seconds (default 8 hours). */
    public static final int SESSION_TTL_SECONDS = getInt("SESSION_TTL_SECONDS", 8 * 60 * 60);

    /** Name of the session cookie. */
    public static final String SESSION_COOKIE = "REVIEW_SESSION";

    /** Base URL used when generating shareable review links. */
    public static final String BASE_URL = get("BASE_URL", "http://localhost:" + PORT);

    private AppConfig() {
    }

    private static String get(String key, String fallback) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) {
            return sys;
        }
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return fallback;
    }

    private static int getInt(String key, int fallback) {
        String value = get(key, String.valueOf(fallback));
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
