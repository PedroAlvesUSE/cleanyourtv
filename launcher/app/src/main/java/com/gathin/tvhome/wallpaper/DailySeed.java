package com.gathin.tvhome.wallpaper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A choice that is the same all day and changes with the date: SHA-256 of "YYYY-MM-DD" (plus an optional salt). */
public final class DailySeed {
    private DailySeed() { }

    /** A non-negative number from the first 8 bytes of the digest. */
    public static long value(LocalDate date, String salt) {
        String text = date.toString() + (salt == null || salt.isEmpty() ? "" : ":" + salt);
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // every Java platform has SHA-256
        }
        long value = 0;
        for (int i = 0; i < 8; i++) value = (value << 8) | (hash[i] & 0xff);
        return value & Long.MAX_VALUE;
    }

    /** An index in [0, n). */
    public static int index(LocalDate date, String salt, int n) {
        if (n <= 0) throw new IllegalArgumentException("n must be positive");
        return (int) (value(date, salt) % n);
    }

    /** A copy of {@code items} in an order that depends only on the date and the salt (Fisher-Yates). */
    public static <T> List<T> shuffle(List<T> items, LocalDate date, String salt) {
        List<T> result = new ArrayList<>(items);
        for (int i = result.size() - 1; i > 0; i--) {
            Collections.swap(result, i, index(date, salt + "#" + i, i + 1));
        }
        return result;
    }
}
