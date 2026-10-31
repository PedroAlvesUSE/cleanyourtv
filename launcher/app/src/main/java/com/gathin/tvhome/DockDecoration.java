package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    /** apple_style: about how far the glass bar reaches above and below the dock tiles (dp), for layout outside. */
    static final float BAR_PAD_DP = 19f;
    /** apple_style: the gap between a focused (grown, risen) tile and the rim, the same on every side (dp). */
    static final float FOCUS_GAP_DP = 12f, MIN_GAP_DP = 6f;

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /** apple_style: a glass bar (frosted wallpaper, light from above, hairline rim) instead of the flat band. */
    private final boolean apple;
    private final Paint frostPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix frostMatrix = new Matrix();
    private final Matrix frostToRoot = new Matrix();
    private final int[] location = new int[2];
    private boolean hasFrost;
    private int accent;
    /** The bar's height when the gradients were last built (they are moved, not rebuilt, as the grid scrolls). */
    private float shadedHeight = Float.NaN;
    private final Matrix shadeMatrix = new Matrix();
    private final Path barPath = new Path();
    private final float[] radii = new float[8];
    /** res/values/apple.xml tokens, read on the first draw (the decoration has no Context of its own). */
    private float focusLift = -1f;
    private int glassColor, borderColor;
    /** Size of a dock tile in the last draw; the bar's padding and radius follow it. */
    private int tileW, tileH;

    /**
     * @param glassEnabled translucent band so wallpaper/glow show through; when false the band is opaque
     * @param opacityPercent band alpha 10–90 when glass is on (ignored when glass is off)
     */
    DockDecoration(int dockSize, float density, boolean glassEnabled, int opacityPercent) {
        this(dockSize, density, glassEnabled, opacityPercent, false);
    }

    DockDecoration(int dockSize, float density, boolean glassEnabled, int opacityPercent, boolean apple) {
        this.dockSize = dockSize;
        this.density = density;
        this.apple = apple;
        if (glassEnabled) {
            int alpha = Config.clampDockGlassOpacity(opacityPercent) * 255 / 100;
            band.setColor((alpha << 24) | 0x00121014);
            edge.setColor(0x26FFFFFF);
        } else {
            band.setColor(0xFF141416);
            edge.setColor(0x1FFFFFFF);
        }
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(Math.max(1f, density));
    }

    /**
     * The frosted wallpaper the glass shows through, mapped like the wallpaper's centre crop. frostToRoot takes the
     * frost bitmap's pixels to root (screen) coordinates; dimAlpha repeats the wallpaper's own dim layer.
     */
    void setFrost(Bitmap frost, Matrix frostToRoot, int dimAlpha) {
        hasFrost = frost != null;
        if (hasFrost) {
            frostPaint.setShader(new BitmapShader(frost, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            this.frostToRoot.set(frostToRoot);
        } else {
            frostPaint.setShader(null);
        }
        dimPaint.setColor(dimAlpha << 24);
    }

    /** auto_accent: a hint of the colour in the rim's highlight; 0 for plain white. */
    void setAccent(int accent) {
        if (this.accent == accent) return;
        this.accent = accent;
        shadedHeight = Float.NaN;   // rebuild the rim gradient
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                tileW = card.getWidth();
                tileH = card.getHeight();
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        if (apple) {
            drawGlass(c, parent);
            return;
        }
        float pad = 14 * density;
        rect.inset(-pad, -pad);
        float radius = 26 * density;
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
    }

    private void drawGlass(Canvas c, RecyclerView parent) {
        if (focusLift < 0f) {
            focusLift = parent.getResources().getDimension(R.dimen.apple_focus_lift);
            glassColor = parent.getContext().getColor(R.color.apple_glass);
            borderColor = parent.getContext().getColor(R.color.apple_glass_border);
            band.setColor(glassColor);
        }
        float grow = (AppleFocus.SCALE - 1f) / 2f;
        // The bar is drawn on the grid's canvas, so it cannot reach past the grid's sides: the gap shrinks to fit.
        float side = Math.min(rect.left, parent.getWidth() - rect.right) - grow * tileW;
        float gap = Math.max(MIN_GAP_DP * density, Math.min(FOCUS_GAP_DP * density, side));
        // Room for the focus zoom and rise, leaving the same gap on every side of a focused tile. The bar stays
        // centred on the row, so below a focused (risen) tile the gap is larger by twice the rise.
        rect.inset(-(gap + grow * tileW), -(gap + grow * tileH + focusLift));
        // Concentric corners: each rim radius is the focused tile's radius plus the gap on that side.
        float tileRadius = AppAdapter.cornerRadius(tileW, density) * AppleFocus.SCALE;
        float top = tileRadius + gap, bottom = top + 2f * focusLift;
        setRadii(radii, top, bottom);
        barPath.rewind();
        barPath.addRoundRect(rect, radii, Path.Direction.CW);
        if (hasFrost) {
            // The bar is fixed to the screen like the wallpaper behind it: map the frost through the grid's
            // position on screen, so it lines up with the picture and moves against the grid when it scrolls.
            parent.getLocationInWindow(location);
            frostMatrix.set(frostToRoot);
            frostMatrix.postTranslate(-location[0], -location[1]);
            frostPaint.getShader().setLocalMatrix(frostMatrix);
            c.drawPath(barPath, frostPaint);
            if (dimPaint.getAlpha() > 0) c.drawPath(barPath, dimPaint);
        } else {
            c.drawPath(barPath, band);
        }
        if (rect.height() != shadedHeight) {
            shadedHeight = rect.height();
            // Glass material: lifted with white towards the top, where the light falls.
            tintPaint.setShader(new LinearGradient(0, 0, 0, shadedHeight,
                    new int[]{0x2EFFFFFF, 0x12FFFFFF, 0x05FFFFFF}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            int lit = accent == 0 ? 0x66FFFFFF : 0x66000000 | (blend(accent, 0xFFFFFF, 0.7f) & 0xFFFFFF);
            rimPaint.setShader(new LinearGradient(0, 0, 0, shadedHeight,
                    new int[]{lit, borderColor, borderColor & 0x14FFFFFF}, new float[]{0f, 0.4f, 1f},
                    Shader.TileMode.CLAMP));
        }
        shadeMatrix.setTranslate(0, rect.top);
        tintPaint.getShader().setLocalMatrix(shadeMatrix);
        rimPaint.getShader().setLocalMatrix(shadeMatrix);
        c.drawPath(barPath, tintPaint);
        float half = rimPaint.getStrokeWidth() / 2f;
        rect.inset(half, half);
        setRadii(radii, top - half, bottom - half);
        barPath.rewind();
        barPath.addRoundRect(rect, radii, Path.Direction.CW);
        c.drawPath(barPath, rimPaint);
    }

    private static void setRadii(float[] r, float top, float bottom) {
        for (int i = 0; i < 4; i++) r[i] = top;
        for (int i = 4; i < 8; i++) r[i] = bottom;
    }

    private static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }
}
