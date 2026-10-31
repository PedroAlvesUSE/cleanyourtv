package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PinGateTest {
    @Test
    public void rightPinPasses() {
        PinGate g = new PinGate("1234");
        assertTrue(g.check("1234", 0));
        assertFalse(g.check("1235", 1));
        assertFalse(g.check(null, 2));
    }

    @Test
    public void fiveWrongTriesLockEverythingForAMinute() {
        PinGate g = new PinGate("1234");
        for (int i = 0; i < PinGate.MAX_FAILS; i++) assertFalse(g.check("0000", i));
        assertTrue(g.locked(10));
        assertFalse("right PIN is refused while locked", g.check("1234", 20));
        assertTrue(g.check("1234", 10 + PinGate.LOCK_MS + 1000));
    }

    @Test
    public void aRightPinResetsTheCount() {
        PinGate g = new PinGate("1234");
        for (int i = 0; i < PinGate.MAX_FAILS - 1; i++) g.check("0000", i);
        assertTrue(g.check("1234", 100));
        for (int i = 0; i < PinGate.MAX_FAILS - 1; i++) g.check("0000", 200 + i);
        assertFalse(g.locked(300));
    }
}
