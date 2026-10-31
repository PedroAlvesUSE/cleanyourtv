package com.gathin.tvhome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;

/**
 * The large, soft shadow under a focused tvOS tile, drawn once into a small alpha mask and stretched over its view.
 * A shadow has no detail, so a quarter-resolution mask filtered up looks the same as a full one and costs a few KB.
 * Two layers: a wide key shadow that falls below the tile and a tight one that keeps its edge grounded. The mask
 * takes its colour from the view's tint (black, or the auto_accent shade).
 */
final class SoftShadow {
    /** Room around the tile for the blur (dp). The shadow view is the tile plus this on every side. */
    static final float PAD_DP = 44f;
    private static final float SCALE = 0.25f;

    /** Masks by size: the tiles share one, the Top Shelf banner has its own. */
    private static final java.util.Map<String, Bitmap> CACHE = new java.util.HashMap<>();

    private SoftShadow() {
    }

    /** A new drawable over the shared mask for a tile of this size; tint it to colour the shadow. */
    static BitmapDrawable drawable(Resources res, int tileW, int tileH, float radius) {
        BitmapDrawable d = new BitmapDrawable(res, mask(tileW, tileH, radius, res.getDisplayMetrics().density));
        d.setFilterBitmap(true);
        return d;
    }

    private static synchronized Bitmap mask(int w, int h, float radius, float density) {
        String key = w + "x" + h + "r" + radius;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        if (CACHE.size() >= 4) CACHE.clear();
        float pad = PAD_DP * density;
        int bw = Math.max(1, Math.round((w + 2 * pad) * SCALE));
        int bh = Math.max(1, Math.round((h + 2 * pad) * SCALE));
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float k = SCALE;
        float l = pad * k, t = pad * k, r = (pad + w) * k, bottom = (pad + h) * k;
        RectF rect = new RectF();
        // Key light from above: a wide, faint shadow, narrower than the tile and dropped well below it.
        float keyBlur = 26f * density * k;
        p.setMaskFilter(new BlurMaskFilter(keyBlur, BlurMaskFilter.Blur.NORMAL));
        p.setAlpha(0x9E);
        float inset = 10f * density * k;
        rect.set(l + inset, t + 14f * density * k, r - inset, bottom + 16f * density * k);
        c.drawRoundRect(rect, radius * k, radius * k, p);
        // Contact shadow: tight and a little darker right at the lower edge.
        p.setMaskFilter(new BlurMaskFilter(7f * density * k, BlurMaskFilter.Blur.NORMAL));
        p.setAlpha(0x5C);
        rect.set(l + 2f * density * k, t + 3f * density * k, r - 2f * density * k, bottom + 3f * density * k);
        c.drawRoundRect(rect, radius * k, radius * k, p);
        CACHE.put(key, b);
        return b;
    }
}
