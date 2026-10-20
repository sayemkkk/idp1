package com.reviewapp.util;

import java.security.SecureRandom;

/**
 * Generates cryptographically strong, URL-safe random tokens used for session
 * ids and for shareable review link tokens.
 */
public final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private TokenGenerator() {
    }

    /** Generates a random token of the requested length. */
    public static String generate(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(chars);
    }

    /** A 32-character session id. */
    public static String sessionId() {
        return generate(32);
    }

    /** A 16-character public form token (uses only URL-safe characters). */
    public static String formToken() {
        return generate(16);
    }
}
