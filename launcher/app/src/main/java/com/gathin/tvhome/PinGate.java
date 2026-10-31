package com.gathin.tvhome;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Confere o PIN das páginas na rede local: 5 erros bloqueiam todas as tentativas por 60 segundos. */
final class PinGate {
    static final int MAX_FAILS = 5;
    static final long LOCK_MS = 60_000;

    private final String pin;
    private int fails;
    private long lockedUntil;

    PinGate(String pin) {
        this.pin = pin;
    }

    /** true se o PIN confere e a porta não está bloqueada. */
    synchronized boolean check(String candidate, long now) {
        if (now < lockedUntil) return false;
        boolean ok = candidate != null && MessageDigest.isEqual(
                pin.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
        if (ok) {
            fails = 0;
            return true;
        }
        if (++fails >= MAX_FAILS) {
            fails = 0;
            lockedUntil = now + LOCK_MS;
        }
        return false;
    }

    synchronized boolean locked(long now) {
        return now < lockedUntil;
    }
}
