package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays and rotates a theme's local wallpaper images while Home is visible. */
final class WallpaperPlayer {
    /** Poll no slower than once a minute when the interval is long. */
    private static final long MAX_POLL_MS = 60_000L;
    private static final long CREDIT_SHOW_MS = 8_000L;
    private static final int CREDIT_FADE_MS = 300;

    private final FrameLayout root;
    private final ImageView image;
    private final ImageView imageAlt;
    private ImageView front;
    private final List<File> files;
    private final long interval;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final int width;
    private final int height;
    private final String transition;
    private final int transitionMs;
    private final boolean animateTransitions;
    /** Credit overlay (wallpaper_credit); null when the function is off. */
    private final LinearLayout creditLayer;
    private final TextView creditTitle;
    private final TextView creditLine;
    private final Context context;
    private Map<String, String[]> credits = java.util.Collections.emptyMap();
    private boolean built;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private boolean transitioning;
    private String displayed = "";
    private Bitmap shown;
    /** Told about each picture once it is on screen (apple_style: dock glass and auto_accent). */
    private java.util.function.Consumer<Bitmap> onShown;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            update();
            schedulePoll();
        }
    };

    private final Runnable hideCredit = new Runnable() {
        @Override
        public void run() {
            if (creditLayer == null) return;
            creditLayer.animate().cancel();
            creditLayer.animate().alpha(0f).setDuration(CREDIT_FADE_MS)
                    .withEndAction(() -> creditLayer.setVisibility(View.GONE)).start();
        }
    };

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.root = root;
        this.context = context;
        files = new ArrayList<>(config.wallpapers);
        interval = config.wallpaperRotationSeconds * 1000L;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        transition = Config.normalizeWallpaperTransition(config.wallpaperTransition);
        transitionMs = config.wallpaperTransitionMs > 0
                ? config.wallpaperTransitionMs : Config.WALLPAPER_TRANSITION_MS_DEFAULT;
        animateTransitions = config.animations && !config.reducedMotion
                && !Config.TRANSITION_NONE.equals(transition);
        image = newImage(context);
        imageAlt = newImage(context);
        imageAlt.setAlpha(0f);
        front = image;
        boolean credit = config.wallpaperEnabled && !files.isEmpty() && config.feature("wallpaper_credit");
        creditLayer = credit ? new LinearLayout(context) : null;
        creditTitle = credit ? new TextView(context) : null;
        creditLine = credit ? new TextView(context) : null;
        if (config.wallpaperEnabled && !files.isEmpty()) {
            built = true;
            int insert = 0;
            root.addView(image, insert++, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (Config.TRANSITION_CROSSFADE.equals(transition)) {
                root.addView(imageAlt, insert++, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            // No shade layer at all when the theme doesn't dim: a full-screen view is overdraw on 1 GB TVs.
            if (config.wallpaperDim > 0) {
                View shade = new View(context);
                shade.setBackgroundColor((config.wallpaperDim * 255 / 100) << 24);
                shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                root.addView(shade, insert, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            if (credit) {
                buildCreditLayer(metrics.density);
                credits = SourcesWallpaper.credits(context);
                // The layer goes over everything else on Home: a short-lived caption, not focusable.
                root.addView(creditLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) (200 * metrics.density), Gravity.BOTTOM | Gravity.START));
            }
            showFirst();
        }
    }

    /** True when Home has a picture to show; false when the plain colour is all there is. */
    boolean hasPictures() {
        return built;
    }

    /**
     * New pictures of the daily search: swaps the list and shows one of them with the usual transition. Main
     * thread only.
     */
    void reload(List<File> fresh) {
        if (closed || !built || fresh.isEmpty()) return;
        files.clear();
        files.addAll(fresh);
        if (creditLayer != null) credits = SourcesWallpaper.credits(context);
        preferences.edit().remove("image").remove("changed").apply();
        update();
    }

    /** Title and credit in white (#F5F5F7) with a shadow, over a dark gradient for contrast. */
    private void buildCreditLayer(float density) {
        creditLayer.setOrientation(LinearLayout.VERTICAL);
        creditLayer.setGravity(Gravity.BOTTOM | Gravity.START);
        creditLayer.setPadding((int) (48 * density), 0, (int) (48 * density), (int) (36 * density));
        creditLayer.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x99000000}));
        creditLayer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        creditLayer.setVisibility(View.GONE);
        creditLayer.setAlpha(0f);
        creditTitle.setTextColor(0xFFF5F5F7);
        creditTitle.setTextSize(22);
        creditTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        creditTitle.setMaxLines(1);
        creditTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        creditTitle.setShadowLayer(6 * density, 0, 2 * density, 0xCC000000);
        creditLine.setTextColor(0xFFF5F5F7);
        creditLine.setTextSize(15);
        creditLine.setMaxLines(1);
        creditLine.setEllipsize(android.text.TextUtils.TruncateAt.END);
        creditLine.setShadowLayer(6 * density, 0, 2 * density, 0xCC000000);
        creditLayer.addView(creditTitle);
        creditLayer.addView(creditLine);
    }

    /** Shows the title and credit of {@code file} for 8 s, fading in and out in 300 ms; nothing for a file without them. */
    private void showCredit(File file) {
        if (creditLayer == null || closed) return;
        handler.removeCallbacks(hideCredit);
        String[] c = credits.get(file.getName());
        if (c == null || (c[0].isEmpty() && c[1].isEmpty())) {
            creditLayer.animate().cancel();
            creditLayer.setVisibility(View.GONE);
            return;
        }
        creditTitle.setText(c[0]);
        creditTitle.setVisibility(c[0].isEmpty() ? View.GONE : View.VISIBLE);
        creditLine.setText(c[1]);
        creditLine.setVisibility(c[1].isEmpty() ? View.GONE : View.VISIBLE);
        creditLayer.animate().cancel();
        creditLayer.setVisibility(View.VISIBLE);
        creditLayer.animate().alpha(1f).setDuration(CREDIT_FADE_MS).start();
        handler.postDelayed(hideCredit, CREDIT_SHOW_MS);
    }

    private static ImageView newImage(Context context) {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    /**
     * The picture Home opens with is decoded right here, on the main thread, as the single wallpaper always was:
     * Home's first frame already has it instead of drawing the plain colour and the picture popping in later.
     * Only the later changes of a rotation decode in the background.
     */
    private void showFirst() {
        String previous = preferences.getString("image", "");
        File first = WallpaperRotation.find(files, previous);
        if (first == null) first = WallpaperRotation.pick(files, previous, random);
        while (first != null) {
            Bitmap bitmap = decode(first);
            if (bitmap != null) {
                front.setImageBitmap(bitmap);
                rememberShown(bitmap);
                displayed = first.getName();
                showCredit(first);
                if (!displayed.equals(previous)) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
                return;
            }
            files.remove(first);   // unreadable or over 4K: try another, or keep the plain colour
            first = WallpaperRotation.pick(files, previous, random);
        }
    }

    void start() {
        active = true;
        handler.removeCallbacks(poll);
        update();
        schedulePoll();
    }

    void stop() {
        active = false;
        handler.removeCallbacks(poll);
        handler.removeCallbacks(hideCredit);
        if (creditLayer != null) {
            creditLayer.animate().cancel();
            creditLayer.setVisibility(View.GONE);
        }
    }

    void close() {
        active = false;
        closed = true;
        handler.removeCallbacks(poll);
        handler.removeCallbacks(hideCredit);
        image.animate().cancel();
        imageAlt.animate().cancel();
        decoder.shutdownNow();
    }

    private void schedulePoll() {
        if (!active || closed || interval <= 0) return;
        long delay = Math.min(Math.max(interval, 1000L), MAX_POLL_MS);
        handler.postDelayed(poll, delay);
    }

    void update() {
        if (!active || closed || loading || transitioning || files.isEmpty()) return;
        String previous = preferences.getString("image", "");
        File selected = WallpaperRotation.find(files, previous);
        long changed = preferences.getLong("changed", 0);
        if (selected == null || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            selected = WallpaperRotation.pick(files, previous, random);
        }
        if (selected == null || selected.getName().equals(displayed)) return;
        File next = selected;
        loading = true;
        decoder.execute(() -> {
            Bitmap bitmap = decode(next);
            root.post(() -> {
                loading = false;
                if (closed || !active) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    files.remove(next);
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                    update();
                    return;
                }
                showCredit(next);
                applyBitmap(bitmap, () -> {
                    rememberShown(bitmap);
                    displayed = next.getName();
                    if (!displayed.equals(previous) || changed == 0) {
                        preferences.edit().putString("image", displayed)
                                .putLong("changed", System.currentTimeMillis()).apply();
                    }
                });
            });
        });
    }

    private void rememberShown(Bitmap bitmap) {
        Bitmap previous = shown;
        shown = bitmap;
        recycleQuietly(previous, bitmap);
        if (onShown != null) onShown.accept(bitmap);
    }

    /** Calls the listener now with the picture on screen (if any), then after every change. */
    void setOnShown(java.util.function.Consumer<Bitmap> listener) {
        onShown = listener;
        if (listener != null && shown != null && !shown.isRecycled()) listener.accept(shown);
    }

    private static void recycleQuietly(Bitmap old, Bitmap keep) {
        if (old == null || old == keep || old.isRecycled()) return;
        try {
            old.recycle();
        } catch (Exception ignored) {
            // bitmap já libertado noutro caminho
        }
    }

    private void applyBitmap(Bitmap bitmap, Runnable after) {
        if (!animateTransitions) {
            front.setImageBitmap(bitmap);
            if (after != null) after.run();
            return;
        }
        transitioning = true;
        // crossfade: swap A/B
        ImageView back = front == image ? imageAlt : image;
        back.setImageBitmap(bitmap);
        back.setAlpha(0f);
        back.animate().cancel();
        front.animate().cancel();
        back.animate().alpha(1f).setDuration(transitionMs).start();
        ImageView outgoing = front;
        front = back;
        outgoing.animate().alpha(0f).setDuration(transitionMs).withEndAction(() -> {
            transitioning = false;
            if (after != null) after.run();
            outgoing.setImageDrawable(null);
        }).start();
    }

    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, this.width, this.height);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, this.width, this.height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }
}
