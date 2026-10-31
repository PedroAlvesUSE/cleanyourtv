package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * The specular light on a focused tvOS tile: a soft oval highlight near the top edge and a faint diagonal sheen.
 * The view is twice the tile's size and centred on it; the tile clips it to its rounded outline. Parallax moves
 * the light by translating this view, so the gradients are drawn once and never redrawn while it slides.
 */
public class GlareView extends View {

    private final Paint spot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Size of the tile this light sits on; the view itself is larger (see class comment). */
    private int tileW, tileH;
    private float strength = 1f;

    public GlareView(Context context) {
        this(context, null);
    }

    public GlareView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** One gradient each and no overlap that alpha would have to compose through a layer. */
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /** The tile's size; the view must be laid out at twice that, centred on the tile. */
    void setTileSize(int w, int h) {
        tileW = w;
        tileH = h;
        rebuild(getWidth(), getHeight());
    }

    /** Lower for artwork that is already bright (the Top Shelf banner), 1 for the focused tile. */
    void setStrength(float s) {
        strength = s;
        rebuild(getWidth(), getHeight());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuild(w, h);
    }

    private void rebuild(int w, int h) {
        if (w <= 0 || h <= 0) return;
        int tw = tileW > 0 ? tileW : w / 2, th = tileH > 0 ? tileH : h / 2;
        float left = (w - tw) / 2f, top = (h - th) / 2f;
        // Oval highlight centred just inside the top edge: bright where the light hits, gone by mid-tile.
        float cx = left + tw * 0.5f, cy = top + th * 0.06f, radius = tw * 0.62f;
        RadialGradient g = new RadialGradient(cx, cy, radius,
                new int[]{alpha(0x52), alpha(0x2B), alpha(0x0E), 0x00FFFFFF},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(1f, 0.62f, cx, cy);   // wider than tall, like light on a glossy poster
        g.setLocalMatrix(m);
        spot.setShader(g);
        // A faint band from the top-left corner that fades out across the tile.
        sheen.setShader(new LinearGradient(left, top, left + tw * 0.75f, top + th,
                new int[]{alpha(0x1F), alpha(0x08), 0x00FFFFFF}, new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        invalidate();
    }

    private int alpha(int a) {
        return Math.round(a * strength) << 24 | 0xFFFFFF;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), spot);
        canvas.drawRect(0, 0, getWidth(), getHeight(), sheen);
    }
}
