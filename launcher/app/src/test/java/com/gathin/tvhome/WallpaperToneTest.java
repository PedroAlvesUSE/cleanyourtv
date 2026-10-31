package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class WallpaperToneTest {

    private static int[] fill(int color, int n) {
        int[] px = new int[n];
        Arrays.fill(px, color);
        return px;
    }

    private static float[] hsv(int color) {
        float[] out = new float[3];
        WallpaperTone.toHsv(color, out);
        return out;
    }

    @Test
    public void greyPictureHasNoAccent() {
        int[] px = new int[300];
        for (int i = 0; i < px.length; i++) {
            int v = i % 256;
            px[i] = 0xFF000000 | v << 16 | v << 8 | v;
        }
        assertEquals(0, WallpaperTone.accentOf(px));
    }

    @Test
    public void dominantHueWins() {
        int[] px = new int[400];
        Arrays.fill(px, 0, 300, 0xFF2060D0);   // blue sky
        Arrays.fill(px, 300, 400, 0xFFD03020);  // a red roof
        float[] accent = hsv(WallpaperTone.accentOf(px));
        assertTrue("hue " + accent[0], accent[0] > 205 && accent[0] < 235);
    }

    @Test
    public void darkWallpaperGivesALightAccent() {
        float[] accent = hsv(WallpaperTone.accentOf(fill(0xFF1A2A10, 200)));   // dark forest green
        assertTrue("value " + accent[2], accent[2] >= 0.78f);
        assertTrue("saturation " + accent[1], accent[1] >= 0.45f && accent[1] <= 0.85f);
        assertTrue("hue " + accent[0], accent[0] > 80 && accent[0] < 110);
    }

    @Test
    public void redAcrossTheWrapIsStillRed() {
        int[] px = new int[200];
        Arrays.fill(px, 0, 100, 0xFFE0103A);   // hue about 350
        Arrays.fill(px, 100, 200, 0xFFE03A10);  // hue about 11
        float h = hsv(WallpaperTone.accentOf(px))[0];
        assertTrue("hue " + h, h > 340 || h < 20);
    }

    @Test
    public void hsvRoundTrip() {
        int c = WallpaperTone.fromHsv(210f, 0.6f, 0.9f);
        float[] back = hsv(c);
        assertEquals(210f, back[0], 1.5f);
        assertEquals(0.6f, back[1], 0.01f);
        assertEquals(0.9f, back[2], 0.01f);
    }
}
