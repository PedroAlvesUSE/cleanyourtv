package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ParentalTest {
    @Test
    public void protectedAppAsksForThePinUntilUnlocked() {
        assertTrue(Parental.needsPin(true, 0, 1000, false));
        assertFalse(Parental.needsPin(true, 5000, 1000, false));
        assertTrue(Parental.needsPin(true, 1000, 1000, false));
    }

    @Test
    public void unprotectedAppIsFreeUnderTheLimit() {
        assertFalse(Parental.needsPin(false, 0, 1000, false));
    }

    @Test
    public void overTheLimitEveryAppAsksForThePin() {
        assertTrue(Parental.needsPin(false, 0, 1000, true));
        assertFalse(Parental.needsPin(false, 9000, 1000, true));
    }

    @Test
    public void limitCountsMinutesAndZeroMeansNoLimit() {
        assertFalse(Parental.overLimit(10 * 3600_000L, 0));
        assertFalse(Parental.overLimit(59 * 60_000L, 60));
        assertTrue(Parental.overLimit(60 * 60_000L, 60));
        assertTrue(Parental.overLimit(61 * 60_000L, 60));
    }

    @Test
    public void unlockLastsThirtyMinutes() {
        Parental.unlock("a.b", false, 1_000_000);
        assertTrue(Parental.unlockedUntil("a.b") == 1_000_000 + Parental.UNLOCK_MS);
        assertTrue(Parental.unlockedUntil(Parental.ANY) < 1_000_000 + Parental.UNLOCK_MS
                || Parental.unlockedUntil(Parental.ANY) == 0);
    }
}
