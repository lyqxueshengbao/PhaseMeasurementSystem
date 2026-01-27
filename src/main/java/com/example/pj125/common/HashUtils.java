package com.example.pj125.common;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtils {
    private HashUtils() {
    }

    /**
     * seedKey = runId + "|" + recipeId + "|" + mode + "|" + repeatIndex
     * seed = first 8 bytes of SHA-256(seedKey) as signed long (big-endian).
     */
    public static long seedFromKey(String seedKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(seedKey.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(bytes, 0, 8).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

