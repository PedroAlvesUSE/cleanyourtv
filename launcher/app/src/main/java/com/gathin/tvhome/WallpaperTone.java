package com.gathin.tvhome;

import android.graphics.Bitmap;

/**
 * What the Apple look takes from the wallpaper on screen: a tiny frosted copy that the dock's glass bar shows
 * through (imitated blur: 96 px wide, box-blurred, stretched back over the bar) and, for auto_accent, its dominant
 * colour. Both come from one 96 px sample, made once per wallpaper change.
 */
final class WallpaperTone {
    static final int SAMPLE_W = 96;
    private static final float FROST_SATURATION = 1.15f;

    /** The frosted copy (opaque ARGB, SAMPLE_W wide); null when there is no wallpaper. */
    final Bitmap frost;
    /** Width and height of the wallpaper bitmap the frost was made from, for the centre-crop mapping. */
    final int sourceW, sourceH;
    /** Dominant colour as 0xFFRRGGBB, or 0 when the picture has no real colour (grey, black and white). */
    final int accent;

    private WallpaperTone(Bitmap frost, int sourceW, int sourceH, int accent) {
        this.frost = frost;
        this.sourceW = sourceW;
        this.sourceH = sourceH;
        this.accent = accent;
    }

    /** Null when the bitmap cannot be read (recycled, empty). Runs in a few ms on the main thread. */
    static WallpaperTone from(Bitmap src) {
        if (src == null || src.isRecycled() || src.getWidth() <= 0 || src.getHeight() <= 0) return null;
        try {
            int w = SAMPLE_W, h = Math.max(1, Math.round(SAMPLE_W * src.getHeight() / (float) src.getWidth()));
            Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
            int[] px = new int[w * h];
            small.getPixels(px, 0, w, 0, 0, w, h);
            if (small != src) small.recycle();
            int accent = accentOf(px);
            saturate(px, FROST_SATURATION);
            for (int pass = 0; pass < 3; pass++) {
                px = HeroArt.boxBlur(px, w, h, 2, true);
                px = HeroArt.boxBlur(px, w, h, 2, false);
            }
            Bitmap frost = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
            return new WallpaperTone(frost, src.getWidth(), src.getHeight(), accent);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private static void saturate(int[] px, float amount) {
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            float lum = 0.2126f * r + 0.7152f * g + 0.0722f * b;
            px[i] = 0xFF000000 | clamp(lum + (r - lum) * amount) << 16 | clamp(lum + (g - lum) * amount) << 8
                    | clamp(lum + (b - lum) * amount);
        }
    }

    private static int clamp(float v) {
        return v < 0 ? 0 : v > 255 ? 255 : Math.round(v);
    }

    /**
     * The dominant colour: pixels vote for one of 24 hue bins, weighted by how colourful and how bright they are;
     * the winning bin (with its neighbours) gives the hue. The result is pushed to a usable accent: saturation
     * 0.45–0.85 and brightness at least 0.78, so a dark or washed-out wallpaper still gives a clear colour. 0 when
     * under 4% of the picture's weight is colour at all. Plain Java, so the JVM tests can run it.
     */
    static int accentOf(int[] px) {
        final int bins = 24;
        float[] weight = new float[bins], sumSat = new float[bins], sumX = new float[bins], sumY = new float[bins];
        float total = 0f;
        float[] hsv = new float[3];
        for (int p : px) {
            toHsv(p, hsv);
            float s = hsv[1], v = hsv[2];
            total += 1f;
            if (s < 0.12f || v < 0.12f) continue;
            float w = s * (0.35f + 0.65f * v);
            int bin = Math.min(bins - 1, (int) (hsv[0] / 360f * bins));
            weight[bin] += w;
            sumSat[bin] += s * w;
            double rad = Math.toRadians(hsv[0]);
            sumX[bin] += (float) Math.cos(rad) * w;
            sumY[bin] += (float) Math.sin(rad) * w;
        }
        int best = -1;
        float bestScore = 0f, colourful = 0f;
        for (int i = 0; i < bins; i++) {
            colourful += weight[i];
            float score = weight[i] + 0.5f * (weight[(i + bins - 1) % bins] + weight[(i + 1) % bins]);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best < 0 || total == 0f || colourful / total < 0.04f) return 0;
        float x = 0f, y = 0f, w = 0f, sat = 0f;
        for (int d = -1; d <= 1; d++) {
            int i = (best + d + bins) % bins;
            x += sumX[i];
            y += sumY[i];
            w += weight[i];
            sat += sumSat[i];
        }
        float hue = (float) Math.toDegrees(Math.atan2(y, x));
        if (hue < 0) hue += 360f;
        float s = Math.max(0.45f, Math.min(0.85f, sat / w * 1.25f));
        return fromHsv(hue, s, 0.9f);
    }

    /** android.graphics.Color.colorToHSV in plain Java: hue 0–360, saturation and value 0–1. */
    static void toHsv(int p, float[] out) {
        float r = ((p >> 16) & 0xFF) / 255f, g = ((p >> 8) & 0xFF) / 255f, b = (p & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0f) h = 0f;
        else if (max == r) h = 60f * (((g - b) / d) % 6f);
        else if (max == g) h = 60f * ((b - r) / d + 2f);
        else h = 60f * ((r - g) / d + 4f);
        if (h < 0) h += 360f;
        out[0] = h;
        out[1] = max == 0f ? 0f : d / max;
        out[2] = max;
    }

    static int fromHsv(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return 0xFF000000 | Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
    }
}
