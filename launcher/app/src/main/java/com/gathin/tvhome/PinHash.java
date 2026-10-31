package com.gathin.tvhome;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PIN de 4 dígitos guardado como "sal:hash" (PBKDF2, sal aleatório de 16 bytes), nunca em texto. */
final class PinHash {
    private static final int ITERATIONS = 10000;

    private PinHash() { }

    static boolean valid(String pin) {
        return pin != null && pin.matches("[0-9]{4}");
    }

    static String encode(String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return encode(pin, salt);
    }

    static String encode(String pin, byte[] salt) {
        return hex(salt) + ":" + hex(derive(pin, salt));
    }

    static boolean verify(String stored, String pin) {
        if (stored == null || !valid(pin)) return false;
        String[] parts = stored.split(":");
        if (parts.length != 2 || !parts[0].matches("[0-9a-f]{32}") || !parts[1].matches("[0-9a-f]{40}")) return false;
        byte[] expected = unhex(parts[1]);
        return MessageDigest.isEqual(expected, derive(pin, unhex(parts[0])));
    }

    private static byte[] derive(String pin, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 160);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static byte[] unhex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return out;
    }
}
