package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * Top Shelf (apple_style + top_shelf): while a dock app has focus, the space above the dock shows that app in the
 * wide tvOS format: its banner large on one side, its name, a caption and the dock position dots on the other,
 * over a wash of its colours that fades out before the dock. Changing apps crossfades two pages with a slight
 * shift in the direction focus moved (250 ms). Only alpha and translation animate.
 */
@SuppressLint("RtlHardcoded")   // the launcher lays out left to right and mirrors Hebrew itself (rtl_menu_bar)
final class TopShelf {
    /** What one app's page shows. */
    static final class Content {
        String key;
        String name;
        CharSequence caption;
        /** Banner, or null to show the icon on a panel of the app's colour. */
        Drawable banner;
        Drawable icon;
        int color;
        /** Softened artwork (HeroArt) for the wash behind the shelf, or null. */
        Bitmap backdrop;
        int dots, dot;
    }

    private static final class Page {
        FrameLayout root;
        View shadow;
        FrameLayout art;
        ImageView banner, icon;
        GlareView glare;
        LinearLayout text;
        TextView name, caption;
        LinearLayout dots;
    }

    private static final int MS = 250;
    private static final float BACKDROP_ALPHA = 0.75f;

    private final Context ctx;
    private final float dp;
    /** Hebrew layout: banner on the left, text on the right. */
    private final boolean rtl;
    /** rtl_app_grid: dock position 0 is on the right, so are the dots. */
    private final boolean rtlGrid;
    private final int screenH;
    private final ImageView[] backdrops = new ImageView[2];
    private final Page[] pages = new Page[2];
    private final DecelerateInterpolator ease = new DecelerateInterpolator(1.8f);
    private final Map<String, Bitmap> washes = new HashMap<>();
    private float washEnd = -1f;
    private int front;
    private String frontKey;
    private int frontPos = -1;
    private boolean shown;
    private int regionTop = Integer.MIN_VALUE, regionBottom = Integer.MIN_VALUE;
    private int accent;

    /**
     * Adds the shelf to root: the colour wash under the scrim (so the clock stays readable) and the pages above
     * the hero, under the grid.
     */
    TopShelf(Context ctx, FrameLayout root, View scrim, View hero, boolean rtl, boolean rtlGrid) {
        this.ctx = ctx;
        this.rtl = rtl;
        this.rtlGrid = rtlGrid;
        dp = ctx.getResources().getDisplayMetrics().density;
        screenH = ctx.getResources().getDisplayMetrics().heightPixels;
        int at = root.indexOfChild(scrim);
        for (int i = 0; i < 2; i++) {
            ImageView wash = new ImageView(ctx);
            wash.setScaleType(ImageView.ScaleType.FIT_XY);
            wash.setAlpha(0f);
            wash.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            wash.forceHasOverlappingRendering(false);
            root.addView(wash, at + i, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            backdrops[i] = wash;
        }
        int pageAt = root.indexOfChild(hero) + 1;
        for (int i = 0; i < 2; i++) {
            pages[i] = newPage();
            root.addView(pages[i].root, pageAt + i, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));
        }
    }

    private Page newPage() {
        Page p = new Page();
        p.root = new FrameLayout(ctx);
        p.root.setAlpha(0f);
        p.root.setClipChildren(false);
        p.root.setClipToPadding(false);
        p.root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        p.shadow = new View(ctx);
        p.root.addView(p.shadow, new FrameLayout.LayoutParams(0, 0));

        p.art = new FrameLayout(ctx);
        final float radius = ctx.getResources().getDimension(R.dimen.apple_card_radius);
        p.art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        p.art.setClipToOutline(true);
        p.banner = new ImageView(ctx);
        p.banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        p.art.addView(p.banner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        p.icon = new ImageView(ctx);
        p.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        p.art.addView(p.icon, new FrameLayout.LayoutParams(0, 0, Gravity.CENTER));
        p.glare = new GlareView(ctx);
        p.glare.setStrength(0.75f);
        p.art.addView(p.glare, new FrameLayout.LayoutParams(0, 0, Gravity.CENTER));
        p.root.addView(p.art, new FrameLayout.LayoutParams(0, 0));

        p.text = new LinearLayout(ctx);
        p.text.setOrientation(LinearLayout.VERTICAL);
        p.text.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        p.name = new TextView(ctx);
        p.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f);
        p.name.setLetterSpacing(-0.01f);
        p.name.setSingleLine(true);
        p.name.setEllipsize(TextUtils.TruncateAt.END);
        p.name.setIncludeFontPadding(false);
        Apple.style(p.name, Apple.MEDIUM, ctx.getColor(R.color.apple_text), dp);
        p.text.addView(p.name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        p.caption = new TextView(ctx);
        p.caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        p.caption.setSingleLine(true);
        p.caption.setEllipsize(TextUtils.TruncateAt.END);
        Apple.style(p.caption, Apple.REGULAR, ctx.getColor(R.color.apple_text_secondary), dp);
        LinearLayout.LayoutParams captionLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        captionLp.topMargin = (int) (10 * dp);
        p.text.addView(p.caption, captionLp);
        p.dots = new LinearLayout(ctx);
        p.dots.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                (int) (6 * dp));
        dotsLp.topMargin = (int) (20 * dp);
        p.text.addView(p.dots, dotsLp);
        p.root.addView(p.text, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return p;
    }

    /** auto_accent colour for the position dots, 0 for white. */
    void setAccent(int accent) {
        this.accent = accent;
        if (frontKey != null) {
            for (Page p : pages) tintDots(p);
        }
    }

    /**
     * Shows this dock app between the header's bottom and the dock's top (root coordinates). False when the space is
     * too small for a shelf; the caller then shows the plain name instead.
     */
    boolean show(Content c, int position, float headerBottom, float dockTop, boolean motion) {
        int top = Math.round(headerBottom + 28 * dp);
        int bottom = Math.round(dockTop - DockDecoration.BAR_PAD_DP * dp - 34 * dp);   // above the dock's glass bar, with a gap
        if (bottom - top < 120 * dp || c.key == null) return false;
        if (top != regionTop || bottom != regionBottom) layout(top, bottom);
        Bitmap wash = wash(c.key, c.backdrop, dockTop);
        if (!shown || frontKey == null) {
            Page p = pages[front];
            if (!c.key.equals(frontKey)) fill(p, c);
            ImageView b = backdrops[front];
            if (!c.key.equals(frontKey)) b.setImageBitmap(wash);
            pages[front ^ 1].root.animate().cancel();
            pages[front ^ 1].root.setAlpha(0f);
            backdrops[front ^ 1].animate().cancel();
            backdrops[front ^ 1].setAlpha(0f);
            p.root.animate().cancel();
            b.animate().cancel();
            if (motion) {
                if (p.root.getAlpha() == 0f) p.root.setTranslationY(10 * dp);
                p.root.setTranslationX(0f);
                p.root.animate().alpha(1f).translationY(0f).setDuration(MS).setInterpolator(ease).withLayer().start();
                b.animate().alpha(BACKDROP_ALPHA).setDuration(MS * 2).setInterpolator(ease).start();
            } else {
                p.root.setAlpha(1f);
                p.root.setTranslationX(0f);
                p.root.setTranslationY(0f);
                b.setAlpha(BACKDROP_ALPHA);
            }
        } else if (!c.key.equals(frontKey)) {
            // Travel direction on screen: positions grow to the right, or to the left on a mirrored grid.
            int dir = (position > frontPos ? 1 : -1) * (rtlGrid ? -1 : 1);
            Page out = pages[front];
            ImageView outWash = backdrops[front];
            front ^= 1;
            Page in = pages[front];
            ImageView inWash = backdrops[front];
            fill(in, c);
            inWash.setImageBitmap(wash);
            out.root.animate().cancel();
            in.root.animate().cancel();
            outWash.animate().cancel();
            inWash.animate().cancel();
            if (motion) {
                float shift = 28 * dp;
                in.root.setAlpha(0f);
                in.root.setTranslationX(dir * shift);
                in.root.setTranslationY(0f);
                in.root.animate().alpha(1f).translationX(0f).setDuration(MS).setInterpolator(ease).withLayer().start();
                out.root.animate().alpha(0f).translationX(-dir * shift * 0.6f).setDuration(MS * 4 / 5)
                        .setInterpolator(ease).withLayer().start();
                inWash.setAlpha(0f);
                inWash.animate().alpha(BACKDROP_ALPHA).setDuration(MS + 100).setInterpolator(ease).start();
                outWash.animate().alpha(0f).setDuration(MS + 100).setInterpolator(ease).start();
            } else {
                in.root.setAlpha(1f);
                in.root.setTranslationX(0f);
                out.root.setAlpha(0f);
                inWash.setAlpha(BACKDROP_ALPHA);
                outWash.setAlpha(0f);
            }
        }
        frontKey = c.key;
        frontPos = position;
        shown = true;
        return true;
    }

    /** Fades the shelf out (focus left the dock); the same app comes back without a crossfade. */
    void hide(boolean motion) {
        if (!shown) return;
        shown = false;
        for (int i = 0; i < 2; i++) {
            pages[i].root.animate().cancel();
            backdrops[i].animate().cancel();
            if (motion) {
                pages[i].root.animate().alpha(0f).setDuration(160).withLayer().start();
                backdrops[i].animate().alpha(0f).setDuration(300).start();
            } else {
                pages[i].root.setAlpha(0f);
                backdrops[i].setAlpha(0f);
            }
        }
    }

    /** Hidden at once, and the next show starts fresh (tiles reloaded). */
    void reset() {
        shown = false;
        frontKey = null;
        frontPos = -1;
        for (int i = 0; i < 2; i++) {
            pages[i].root.animate().cancel();
            pages[i].root.setAlpha(0f);
            backdrops[i].animate().cancel();
            backdrops[i].setAlpha(0f);
        }
    }

    /** Ends any running fade where it was going (Home left mid-change). */
    void settle() {
        for (int i = 0; i < 2; i++) {
            pages[i].root.animate().cancel();
            backdrops[i].animate().cancel();
            boolean on = shown && i == front;
            pages[i].root.setAlpha(on ? 1f : 0f);
            pages[i].root.setTranslationX(0f);
            pages[i].root.setTranslationY(0f);
            backdrops[i].setAlpha(on ? BACKDROP_ALPHA : 0f);
        }
    }

    /** Drops the cached washes (tiles reloaded: an app may have new artwork). */
    void clearCache() {
        washes.clear();
    }

    private void layout(int top, int bottom) {
        regionTop = top;
        regionBottom = bottom;
        int pad = Math.round(SoftShadow.PAD_DP * dp);
        int h = bottom - top;
        int bannerH = Math.min(h, Math.round(206 * dp));
        int bannerW = Math.round(bannerH * 16f / 9f);
        float radius = ctx.getResources().getDimension(R.dimen.apple_card_radius);
        int side = Math.round(68 * dp);
        for (Page p : pages) {
            // The page reaches below the banner by the shadow's room, so a layer during a fade keeps the shadow.
            FrameLayout.LayoutParams rootLp = (FrameLayout.LayoutParams) p.root.getLayoutParams();
            rootLp.topMargin = top;
            rootLp.height = h + pad;
            p.root.setLayoutParams(rootLp);
            int edge = rtl ? Gravity.LEFT : Gravity.RIGHT;
            FrameLayout.LayoutParams artLp = new FrameLayout.LayoutParams(bannerW, bannerH, Gravity.BOTTOM | edge);
            artLp.bottomMargin = pad;
            if (rtl) artLp.leftMargin = side;
            else artLp.rightMargin = side;
            p.art.setLayoutParams(artLp);
            FrameLayout.LayoutParams shadowLp = new FrameLayout.LayoutParams(bannerW + 2 * pad, bannerH + 2 * pad,
                    Gravity.BOTTOM | edge);
            if (rtl) shadowLp.leftMargin = side - pad;
            else shadowLp.rightMargin = side - pad;
            p.shadow.setLayoutParams(shadowLp);
            p.shadow.setBackground(SoftShadow.drawable(ctx.getResources(), bannerW, bannerH, radius));
            p.shadow.setTranslationY(4 * dp);
            int iconSize = Math.round(bannerH * 0.42f);
            p.icon.setLayoutParams(new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
            p.glare.setLayoutParams(new FrameLayout.LayoutParams(2 * bannerW, 2 * bannerH, Gravity.CENTER));
            p.glare.setTileSize(bannerW, bannerH);
            FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | (rtl ? Gravity.RIGHT : Gravity.LEFT));
            textLp.bottomMargin = pad + Math.round(4 * dp);
            int textSide = side + bannerW + Math.round(56 * dp);
            textLp.leftMargin = rtl ? textSide : side;
            textLp.rightMargin = rtl ? side : textSide;
            p.text.setLayoutParams(textLp);
        }
    }

    private void fill(Page p, Content c) {
        p.name.setText(c.name);
        if (TextUtils.isEmpty(c.caption)) {
            p.caption.setVisibility(View.GONE);
        } else {
            p.caption.setText(c.caption);
            p.caption.setVisibility(View.VISIBLE);
        }
        if (c.banner != null) {
            p.art.setBackgroundColor(0xFF1C1C1E);
            p.banner.setImageDrawable(c.banner);
            p.banner.setVisibility(View.VISIBLE);
            p.icon.setImageDrawable(null);
            p.icon.setVisibility(View.GONE);
        } else {
            // No banner: the icon on a panel of the app's own colour, like a tvOS app without Top Shelf art.
            int col = c.color == 0 ? 0xFF3A3A3C : c.color;
            GradientDrawable panel = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{shade(col, 0.62f), shade(col, 0.32f)});
            p.art.setBackground(panel);
            p.banner.setImageDrawable(null);
            p.banner.setVisibility(View.GONE);
            p.icon.setImageDrawable(c.icon);
            p.icon.setVisibility(View.VISIBLE);
        }
        int count = c.dots;
        p.dots.setVisibility(count > 1 ? View.VISIBLE : View.GONE);
        if (p.dots.getChildCount() != count) {
            p.dots.removeAllViews();
            for (int i = 0; i < count; i++) {
                View dot = new View(ctx);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(3 * dp);
                dot.setBackground(bg);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (6 * dp), (int) (6 * dp));
                if (i > 0) lp.leftMargin = (int) (7 * dp);
                p.dots.addView(dot, lp);
            }
        }
        for (int i = 0; i < count; i++) {
            View dot = p.dots.getChildAt(rtlGrid ? count - 1 - i : i);
            boolean on = i == c.dot;
            dot.setTag(on);
            ViewGroup.LayoutParams lp = dot.getLayoutParams();
            int w = (int) ((on ? 22 : 6) * dp);
            if (lp.width != w) {
                lp.width = w;
                dot.setLayoutParams(lp);
            }
        }
        tintDots(p);
    }

    private void tintDots(Page p) {
        int on = Apple.light(accent);
        for (int i = 0; i < p.dots.getChildCount(); i++) {
            View dot = p.dots.getChildAt(i);
            ((GradientDrawable) dot.getBackground()).setColor(Boolean.TRUE.equals(dot.getTag()) ? on : 0x59FFFFFF);
        }
    }

    /**
     * The artwork wash, faded out by rows towards the dock: the bitmap is 27 rows high and stretched over the
     * screen, so baking the alpha into it gives a smooth fade with no mask layer.
     */
    private Bitmap wash(String key, Bitmap art, float dockTop) {
        if (art == null) return null;
        float end = Math.max(0.3f, Math.min(1f, (dockTop - 24 * dp) / screenH));
        if (Math.abs(end - washEnd) > 0.01f) {
            washes.clear();
            washEnd = end;
        }
        Bitmap hit = washes.get(key);
        if (hit != null) return hit;
        int w = art.getWidth(), h = art.getHeight();
        int[] px = new int[w * h];
        art.getPixels(px, 0, w, 0, 0, w, h);
        float[] hsv = new float[3];
        for (int i = 0; i < px.length; i++) {
            // Deep and rich rather than milky: a white banner would otherwise grey out the whole top of the screen.
            WallpaperTone.toHsv(px[i], hsv);
            px[i] = WallpaperTone.fromHsv(hsv[0], Math.min(1f, hsv[1] * 1.3f), Math.min(hsv[2], 0.5f) * 0.85f);
        }
        for (int y = 0; y < h; y++) {
            float t = ((y + 0.5f) / h - end * 0.3f) / (end * 0.7f);
            t = Math.max(0f, Math.min(1f, t));
            float a = 1f - t * t * (3f - 2f * t);   // smoothstep: full at the top, gone at the dock
            int alpha = Math.round(a * 255f) << 24;
            for (int x = 0; x < w; x++) px[y * w + x] = alpha | (px[y * w + x] & 0xFFFFFF);
        }
        Bitmap out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        washes.put(key, out);
        return out;
    }

    private static int shade(int c, float v) {
        int r = Math.round(((c >> 16) & 0xFF) * v), g = Math.round(((c >> 8) & 0xFF) * v), b = Math.round((c & 0xFF) * v);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
