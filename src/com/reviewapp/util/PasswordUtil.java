package com.reviewapp.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Password hashing utilities based on SHA-256 as required by the project spec.
 *
 * <p>A per-user random salt is generated for every account so identical
 * passwords never produce identical stored hashes, which defeats rainbow
 * table attacks. The stored format is {@code hexSalt:hexHash}.</p>
 */
public final class PasswordUtil {

    private static final String ALGORITHM = "SHA-256";
    private static final int SALT_BYTES = 16;

    private PasswordUtil() {
    }

    /**
     * Hashes the given plaintext password using a freshly generated salt and
     * returns the composite {@code salt:hash} representation for storage.
     */
    public static String hash(String password) {
        byte[] salt = randomSalt();
        byte[] hash = digest(password, salt);
        return toHex(salt) + ":" + toHex(hash);
    }

    /**
     * Verifies a plaintext password against a stored {@code salt:hash} value in
     * constant time to mitigate timing attacks.
     */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        int sep = stored.indexOf(':');
        if (sep < 0) {
            return false;
        }
        String saltHex = stored.substring(0, sep);
        String hashHex = stored.substring(sep + 1);
        byte[] salt = fromHex(saltHex);
        byte[] expected = fromHex(hashHex);
        byte[] actual = digest(password, salt);
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] digest(String password, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            md.update(salt);
            md.update(password.getBytes(StandardCharsets.UTF_8));
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static byte[] randomSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new java.security.SecureRandom().nextBytes(salt);
        return salt;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Invalid hex string length");
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }
}
