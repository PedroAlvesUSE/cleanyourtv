package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QrTest {
    @Test
    public void versionGrowsWithTheText() {
        assertEquals(1, Qr.version(17));
        assertEquals(2, Qr.version(18));
        assertEquals(3, Qr.version(40));
        assertEquals(5, Qr.version(106));
        assertEquals(0, Qr.version(107));
    }

    @Test
    public void tooLongTextHasNoCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 107; i++) sb.append('a');
        assertNull(Qr.encode(sb.toString()));
    }

    @Test
    public void urlGivesAVersionTwoSymbol() {
        boolean[][] m = Qr.encode("http://192.168.3.21:8766/");
        assertNotNull(m);
        assertEquals(25, m.length);   // versão 2 tem 25x25 módulos e cabe 32 bytes; a URL tem 25
    }

    @Test
    public void finderPatternsAndTimingAreInPlace() {
        boolean[][] m = Qr.encode("http://10.0.0.5:8766/");
        int n = m.length;
        int[][] corners = {{0, 0}, {n - 7, 0}, {0, n - 7}};
        for (int[] c : corners) {
            for (int dy = 0; dy < 7; dy++) {
                for (int dx = 0; dx < 7; dx++) {
                    boolean ring = dx == 0 || dx == 6 || dy == 0 || dy == 6;
                    boolean core = dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4;
                    assertEquals(ring || core, m[c[1] + dy][c[0] + dx]);
                }
            }
        }
        for (int i = 8; i < n - 8; i++) {
            assertEquals(i % 2 == 0, m[6][i]);
            assertEquals(i % 2 == 0, m[i][6]);
        }
        assertTrue("dark module", m[n - 8][8]);
    }

    @Test
    public void formatBitsMatchTheStandardTableForLevelL() {
        int[] expected = {0x77C4, 0x72F3, 0x7DAA, 0x789D, 0x662F, 0x6318, 0x6C41, 0x6976};
        for (int mask = 0; mask < 8; mask++) assertEquals(expected[mask], Qr.formatBits(mask));
    }

    @Test
    public void reedSolomonMatchesTheSpecExample() {
        // ISO 18004 Annex I: "01234567" in version 1-M gives these 10 error words.
        byte[] data = {0x10, 0x20, 0x0C, 0x56, 0x61, (byte) 0x80, (byte) 0xEC, 0x11, (byte) 0xEC, 0x11, (byte) 0xEC, 0x11,
                (byte) 0xEC, 0x11, (byte) 0xEC, 0x11};
        byte[] ec = Qr.reedSolomon(data, 10);
        int[] expected = {0xA5, 0x24, 0xD4, 0xC1, 0xED, 0x36, 0xC7, 0x87, 0x2C, 0x55};
        for (int i = 0; i < 10; i++) assertEquals(expected[i], ec[i] & 0xFF);
    }

    @Test
    public void codewordsStartWithTheByteModeHeader() {
        byte[] words = Qr.codewords("A".getBytes(), 1);
        assertEquals(26, words.length);
        assertEquals(0x40, words[0] & 0xF0);   // modo byte 0100
        assertFalse(words[1] == 0);
    }
}
