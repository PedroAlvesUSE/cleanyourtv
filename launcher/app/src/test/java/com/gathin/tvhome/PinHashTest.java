package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PinHashTest {
    @Test
    public void onlyFourDigitsAreValid() {
        assertTrue(PinHash.valid("0000"));
        assertTrue(PinHash.valid("1234"));
        assertFalse(PinHash.valid("123"));
        assertFalse(PinHash.valid("12345"));
        assertFalse(PinHash.valid("12a4"));
        assertFalse(PinHash.valid(""));
        assertFalse(PinHash.valid(null));
    }

    @Test
    public void correctPinVerifiesAndWrongPinDoesNot() {
        String stored = PinHash.encode("4821");
        assertTrue(PinHash.verify(stored, "4821"));
        assertFalse(PinHash.verify(stored, "4822"));
        assertFalse(PinHash.verify(stored, "abcd"));
        assertFalse(PinHash.verify(stored, null));
    }

    @Test
    public void storedValueIsSaltedAndHidesThePin() {
        String a = PinHash.encode("1111");
        String b = PinHash.encode("1111");
        assertNotEquals("same PIN, different salt", a, b);
        assertFalse(a.contains("1111:"));
        assertTrue(a.matches("[0-9a-f]{32}:[0-9a-f]{40}"));
    }

    @Test
    public void fixedSaltGivesAStableHash() {
        byte[] salt = new byte[16];
        assertEquals(PinHash.encode("0000", salt), PinHash.encode("0000", salt));
        assertNotEquals(PinHash.encode("0000", salt), PinHash.encode("0001", salt));
    }

    @Test
    public void malformedStoredValuesNeverVerify() {
        assertFalse(PinHash.verify(null, "1234"));
        assertFalse(PinHash.verify("", "1234"));
        assertFalse(PinHash.verify("1234", "1234"));
        assertFalse(PinHash.verify("zz:zz", "1234"));
    }
}
