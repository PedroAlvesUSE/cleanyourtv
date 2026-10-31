package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Recommended row on Home (recommended_row): 16:9 cards of popular YouTube videos from the public Piped
 * and Invidious instances, in the dock's columns. AboveDock owns its place on screen (just above the dock);
 * this class only builds the cards. OK opens the video in TizenTube. The row is gone when the list is empty.
 */
final class RecommendedRow {

    private static final String TIZENTUBE = "io.gh.reisxd.tizentube.cobalt";
    private static final float PAD_DP = 10, BOTTOM_DP = 8;

    private final Activity activity;
    private final ContinueRow.Host host;
    private final RecyclerView grid;
    private final int columns;
    private final float density;
    private final boolean animations, parallax, reduced;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<RecommendedProvider.Item> items = new ArrayList<>();
    private final Map<String, Drawable> icons = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();

    final LinearLayout view;
    private final RecyclerView list;
    private final RowAdapter adapter = new RowAdapter();
    private int tileW, tileH, cellW;
    private boolean started;
    private Runnable onChanged;

    RecommendedRow(Activity activity, ContinueRow.Host host, RecyclerView grid, int columns,
                   boolean parallax, boolean reduced) {
        this.activity = activity;
        this.host = host;
        this.grid = grid;
        this.columns = columns;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.animations = activity.getResources().getBoolean(R.bool.animations);
        this.parallax = parallax;
        this.reduced = reduced;

        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setClipChildren(false);
        view.setClipToPadding(false);
        view.setVisibility(View.GONE);
        list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false));
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setItemAnimator(null);
        list.setHasFixedSize(true);
        list.setAdapter(adapter);
        view.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> grid.post(this::measure));
    }

    void start() {
        if (started) return;
        started = true;
        load();
    }

    void stop() {
        started = false;
    }

    void destroy() {
        stop();
    }

    boolean shown() {
        return !items.isEmpty() && view.getVisibility() == View.VISIBLE;
    }

    /** True while there is at least one item, whether or not AboveDock is showing this page now. */
    boolean hasItems() {
        return !items.isEmpty();
    }

    int columns() {
        return columns;
    }

    /** AboveDock listens here to pick a page and place the shelf when the items change. */
    void setOnChanged(Runnable r) {
        onChanged = r;
    }

    // ---- data ------------------------------------------------------------------------------------

    private void load() {
        RecommendedProvider.fetch(activity, "BR", out -> {
            if (activity.isDestroyed()) return;
            apply(out);
        });
    }

    private void apply(List<RecommendedProvider.Item> fresh) {
        View focused = list.getFocusedChild();
        int focusIndex = focused == null ? -1 : list.getChildAdapterPosition(focused);
        items.clear();
        items.addAll(fresh);
        if (!icons.containsKey(TIZENTUBE)) {
            try {
                ApplicationInfo ai = activity.getPackageManager().getApplicationInfo(TIZENTUBE, 0);
                icons.put(TIZENTUBE, ai.loadIcon(activity.getPackageManager()));
                labels.put(TIZENTUBE, ai.loadLabel(activity.getPackageManager()).toString());
            } catch (Exception ignored) {
                icons.put(TIZENTUBE, null);
                labels.put(TIZENTUBE, TIZENTUBE);
            }
        }
        adapter.notifyDataSetChanged();
        if (items.isEmpty()) {
            view.setVisibility(View.GONE);
            if (onChanged != null) onChanged.run();
            return;
        }
        view.setVisibility(View.VISIBLE);
        measure();
        if (focusIndex >= 0) {
            int target = Math.min(focusIndex, items.size() - 1);
            list.post(() -> focusIndex(target));
        }
        if (onChanged != null) onChanged.run();
    }

    // ---- layout ----------------------------------------------------------------------------------

    /** Sizes the cards to the dock's tiles and columns. */
    private void measure() {
        if (items.isEmpty() || grid.getWidth() == 0) return;
        int inner = grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight();
        int cell = inner / columns;
        int w = 0;
        View dock = dockChild();
        if (dock != null) {
            View card = dock.findViewById(R.id.card);
            if (card != null) w = card.getWidth();
        }
        if (w <= 0) w = AppAdapter.tileWidthPx(inner, columns, density);
        if (w != tileW || cell != cellW) {
            tileW = w;
            tileH = Math.round(w * 9f / 16f);
            cellW = cell;
            list.setPadding(grid.getPaddingLeft(), 0, grid.getPaddingRight(), 0);
            list.setAdapter(adapter);   // new holders at the new size
        }
    }

    private int itemHeight() {
        return tileH + Math.round((PAD_DP + BOTTOM_DP) * density);
    }

    private View dockChild() {
        if (grid.getAdapter() == null) return null;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View c = grid.getChildAt(i);
            int pos = grid.getChildAdapterPosition(c);
            if (pos >= 0 && pos < columns) return c;
        }
        return null;
    }

    // ---- focus -----------------------------------------------------------------------------------

    boolean focusIndex(int index) {
        RecyclerView.ViewHolder vh = list.findViewHolderForAdapterPosition(index);
        if (vh != null) return ((Holder) vh).card.requestFocus();
        list.scrollToPosition(index);
        list.post(() -> {
            RecyclerView.ViewHolder h = list.findViewHolderForAdapterPosition(index);
            if (h != null) ((Holder) h).card.requestFocus();
        });
        return true;
    }

    private int columnOf(int index) {
        return Math.max(0, Math.min(columns - 1, index));
    }

    // ---- actions ---------------------------------------------------------------------------------

    private void open(RecommendedProvider.Item it) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + it.videoId))
                .setPackage(TIZENTUBE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            activity.startActivity(i);
            return;
        } catch (Exception ignored) {
            // TizenTube did not take the link: open the app.
        }
        PackageManager pm = activity.getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(TIZENTUBE);
        if (launch == null) launch = pm.getLaunchIntentForPackage(TIZENTUBE);
        try {
            if (launch == null) throw new IllegalStateException();
            activity.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (Exception e) {
            Toast.makeText(activity, activity.getString(R.string.cant_open, labels.get(TIZENTUBE)),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ---- cards -----------------------------------------------------------------------------------

    final class Holder extends RecyclerView.ViewHolder {
        final FrameLayout card;
        final ImageView image, placeholderIcon;
        final TextView title, app;
        final ImageView appIcon;
        final AppleFocus focus;
        String imageKey;

        Holder(FrameLayout item, FrameLayout card, ImageView image, ImageView placeholderIcon,
               TextView title, ImageView appIcon, TextView app, AppleFocus focus) {
            super(item);
            this.card = card;
            this.image = image;
            this.placeholderIcon = placeholderIcon;
            this.title = title;
            this.appIcon = appIcon;
            this.app = app;
            this.focus = focus;
        }
    }

    final class RowAdapter extends RecyclerView.Adapter<Holder> {
        @Override
        public int getItemCount() {
            return tileW == 0 ? 0 : items.size();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup group, int viewType) {
            return create();
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            bind(h, items.get(position));
        }
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private Holder create() {
        Context ctx = activity;
        int w = tileW, h = tileH;
        FrameLayout item = new FrameLayout(ctx);
        item.setClipChildren(false);
        item.setClipToPadding(false);
        item.setPadding(0, dp(PAD_DP), 0, 0);
        item.setLayoutParams(new RecyclerView.LayoutParams(cellW, itemHeight()));

        int pad = dp(SoftShadow.PAD_DP);
        float radius = AppAdapter.cornerRadius(w, density);
        View shadow = new View(ctx);
        shadow.setBackground(SoftShadow.drawable(ctx.getResources(), w, h, radius));
        shadow.setAlpha(0f);
        shadow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(w + 2 * pad, h + 2 * pad,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        slp.topMargin = -pad;
        slp.bottomMargin = -pad;
        item.addView(shadow, slp);

        FrameLayout card = new FrameLayout(ctx);
        card.setFocusable(true);
        card.setClickable(true);
        card.setLongClickable(true);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF2C2C30, 0xFF1C1C1F});
        card.setBackground(bg);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        card.setClipToOutline(true);
        item.addView(card, new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        ImageView placeholderIcon = new ImageView(ctx);
        placeholderIcon.setAlpha(0.35f);
        card.addView(placeholderIcon, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
        ImageView image = new ImageView(ctx);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        View scrim = new View(ctx);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xB3000000}));
        card.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(h * 0.55f), Gravity.BOTTOM));
        GlareView glare = new GlareView(ctx);
        glare.setAlpha(0f);
        card.addView(glare, new FrameLayout.LayoutParams(2 * w, 2 * h, Gravity.CENTER));
        glare.setTileSize(w, h);

        // The title and the channel sit on the artwork, like the tvOS shelves: the row stays one tile tall.
        LinearLayout textBlock = new LinearLayout(ctx);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(ctx);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        Apple.style(title, Apple.MEDIUM, Apple.TEXT, density);
        textBlock.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout appRow = new LinearLayout(ctx);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView appIcon = new ImageView(ctx);
        appRow.addView(appIcon, new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView app = new TextView(ctx);
        app.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        app.setSingleLine(true);
        app.setEllipsize(TextUtils.TruncateAt.END);
        Apple.style(app, Apple.REGULAR, Apple.TEXT_2, density);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        alp.leftMargin = dp(5);
        appRow.addView(app, alp);
        LinearLayout.LayoutParams appLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        appLp.topMargin = dp(3);
        textBlock.addView(appRow, appLp);
        FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        textLp.leftMargin = dp(12);
        textLp.rightMargin = dp(12);
        textLp.bottomMargin = dp(11);
        card.addView(textBlock, textLp);

        AppleFocus focus = new AppleFocus(item, card, shadow, glare, animations, parallax, reduced);
        focus.setTileSize(w, h);
        Holder holder = new Holder(item, card, image, placeholderIcon, title, appIcon, app, focus);
        title.setAlpha(0.8f);
        card.setOnFocusChangeListener((v, has) -> {
            focus.onFocusChanged(has);
            float a = has ? 1f : 0.8f;
            if (Apple.motion(animations)) title.animate().alpha(a).setDuration(150).start();
            else title.setAlpha(a);
            if (has) host.onRowFocused();
        });
        card.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) open(items.get(pos));
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    focus.onKey(keyCode);
                    host.onRowUp(columnOf(pos));
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    focus.onKey(keyCode);
                    host.onRowDown(columnOf(pos));
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    focus.onKey(keyCode);
                    boolean left = keyCode == KeyEvent.KEYCODE_DPAD_LEFT;
                    if (left && pos == 0 || !left && pos == items.size() - 1) return true;
                    focusIndex(pos + (left ? -1 : 1));
                    return true;
                default:
                    return false;
            }
        });
        return holder;
    }

    private void bind(Holder h, RecommendedProvider.Item it) {
        h.title.setText(it.title);
        h.app.setText(it.channel != null ? it.channel : labels.get(TIZENTUBE));
        Drawable icon = icons.get(TIZENTUBE);
        h.appIcon.setImageDrawable(icon);
        h.placeholderIcon.setImageDrawable(icon);
        h.card.setContentDescription(it.title + (it.channel == null ? "" : ", " + it.channel));
        h.focus.snap(h.card.isFocused());
        String key = it.thumb;
        h.imageKey = key;
        Bitmap cached = ContinueImages.cached(key);
        if (cached != null) {
            h.image.setImageBitmap(cached);
            h.image.setAlpha(1f);
            h.placeholderIcon.setVisibility(View.INVISIBLE);
            return;
        }
        h.image.setImageDrawable(null);
        h.image.setAlpha(0f);
        h.placeholderIcon.setVisibility(View.VISIBLE);
        ContinueImages.load(activity, key, tileW, b -> {
            if (b == null || !key.equals(h.imageKey)) return;
            h.image.setImageBitmap(b);
            h.placeholderIcon.setVisibility(View.INVISIBLE);
            if (Apple.motion(animations) && !reduced) h.image.animate().alpha(1f).setDuration(220).start();
            else h.image.setAlpha(1f);
        });
    }
}
