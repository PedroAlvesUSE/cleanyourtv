package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Continue watching row on Home (continue_watching): 16:9 cards in the dock's columns, just under the dock.
 * The grid keeps a gap under the dock row (an item decoration) and this row is drawn over that gap, following the
 * grid as it scrolls. The row is gone when the list is empty. OK opens the item's deep link inside its own app
 * (or the app); holding OK offers "Remove from list". Built only when the switch is on.
 */
final class ContinueRow {

    interface Host {
        /** Up from the card in this dock column. */
        void onRowUp(int column);

        /** Down from the card in this column; false when there is nothing below. */
        boolean onRowDown(int column);

        /** A card took focus. */
        void onRowFocused();

        /**
         * Left of the first card or right of the last. True when AboveDock switches section
         * (Continue ↔ Recommended); false keeps focus on the edge card.
         */
        boolean onRowSide(boolean right);
    }

    private final Activity activity;
    private final Host host;
    private final FrameLayout parent;
    private final RecyclerView grid;
    private final int columns;
    private final float density;
    private final boolean animations, parallax, reduced;
    private final Set<String> hidden;
    private final boolean media;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final Map<String, Drawable> icons = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();
    private final List<ContinueStore.Item> items = new ArrayList<>();

    final LinearLayout view;
    private final TextView header;
    private final RecyclerView list;
    private final RowAdapter adapter = new RowAdapter();
    private final Gap gap = new Gap();
    private int gapPx;
    private int tileW, tileH, cellW;
    private boolean started;
    /** Set when AboveDock owns the row: it positions the row and reserves no grid gap. */
    private final boolean hosted;
    private Runnable onChanged;
    private final Runnable reload = this::reload;
    private final ContentObserver observer;

    ContinueRow(Activity activity, Host host, RecyclerView grid, int columns, Config cfg, boolean parallax,
                boolean reduced) {
        this(activity, host, grid, columns, cfg, parallax, reduced, false);
    }

    ContinueRow(Activity activity, Host host, RecyclerView grid, int columns, Config cfg, boolean parallax,
                boolean reduced, boolean hosted) {
        this.activity = activity;
        this.host = host;
        this.grid = grid;
        this.parent = (FrameLayout) grid.getParent();
        this.columns = columns;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.animations = cfg.animations;
        this.parallax = parallax;
        this.reduced = reduced;
        this.hosted = hosted;
        this.hidden = new java.util.HashSet<>(cfg.hiddenApps);
        this.media = cfg.feature(ContinueStore.FEATURE_MEDIA);

        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setClipChildren(false);
        view.setClipToPadding(false);
        view.setVisibility(View.GONE);
        header = new TextView(activity);
        header.setText(R.string.cw_title);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        header.setSingleLine(true);
        Apple.style(header, Apple.MEDIUM, Apple.TEXT_2, density);
        view.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        // Hosted: the tab header of AboveDock already names the page.
        if (hosted) header.setVisibility(View.GONE);
        list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false));
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setItemAnimator(null);
        list.setHasFixedSize(true);
        list.setAdapter(adapter);
        if (hosted) {
            list.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull android.graphics.Rect out, @NonNull View view,
                                           @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    int pos = parent.getChildAdapterPosition(view);
                    if (pos >= 0 && pos < items.size() - 1) out.right = Math.round(16 * density);
                }
            });
        }
        view.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (!hosted) {
            parent.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
            grid.addItemDecoration(gap);
        }

        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                place();
            }
        });
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> grid.post(this::measure));
        observer = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                main.removeCallbacks(reload);
                main.postDelayed(reload, 300);
            }
        };
    }

    void start() {
        if (started) return;
        started = true;
        activity.getContentResolver().registerContentObserver(ContinueProvider.ITEMS, false, observer);
        reload();
    }

    void stop() {
        if (!started) return;
        started = false;
        main.removeCallbacks(reload);
        activity.getContentResolver().unregisterContentObserver(observer);
    }

    void destroy() {
        stop();
        work.shutdown();
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

    private void reload() {
        Context app = activity.getApplicationContext();
        work.execute(() -> {
            PackageManager pm = app.getPackageManager();
            Map<String, Boolean> allowed = new HashMap<>();
            List<ContinueStore.Item> all = ContinueProvider.load(app);
            List<ContinueStore.Item> out = ContinueStore.visible(all, it -> {
                if (hidden.contains(it.pkg)) return false;
                Boolean ok = allowed.get(it.pkg + it.source);
                if (ok == null) {
                    ok = installed(pm, it.pkg) && (ContinueStore.SOURCE_MEDIA.equals(it.source) ? media
                            : ContinueApps.shown(app, it.pkg));
                    allowed.put(it.pkg + it.source, ok);
                }
                return ok;
            }, System.currentTimeMillis());
            Map<String, Drawable> newIcons = new HashMap<>();
            Map<String, String> newLabels = new HashMap<>();
            for (ContinueStore.Item it : out) {
                if (newIcons.containsKey(it.pkg) || icons.containsKey(it.pkg)) continue;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(it.pkg, 0);
                    newIcons.put(it.pkg, ai.loadIcon(pm));
                    newLabels.put(it.pkg, ai.loadLabel(pm).toString());
                } catch (Exception ignored) {
                    newIcons.put(it.pkg, null);
                    newLabels.put(it.pkg, it.pkg);
                }
            }
            main.post(() -> {
                if (activity.isDestroyed()) return;
                icons.putAll(newIcons);
                labels.putAll(newLabels);
                apply(out);
            });
        });
    }

    private static boolean installed(PackageManager pm, String pkg) {
        try {
            pm.getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @android.annotation.SuppressLint("NotifyDataSetChanged")
    private void apply(List<ContinueStore.Item> fresh) {
        View focused = list.getFocusedChild();
        int focusIndex = focused == null ? -1 : list.getChildAdapterPosition(focused);
        items.clear();
        if (hosted) {
            int n = Math.min(AboveDock.MAX_CARDS, fresh.size());
            for (int i = 0; i < n; i++) items.add(fresh.get(i));
        } else {
            items.addAll(fresh);
        }
        adapter.notifyDataSetChanged();
        if (items.isEmpty()) {
            boolean hadFocus = focused != null;
            view.setVisibility(View.GONE);
            setGap(0);
            if (onChanged != null) onChanged.run();
            if (hadFocus) host.onRowUp(0);
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

    /** Dock-sized tiles when the row stands alone; two large posters when AboveDock hosts it. */
    private void measure() {
        if (items.isEmpty() || grid.getWidth() == 0) return;
        int inner = grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight();
        int cell;
        int w;
        if (hosted) {
            int gap = Math.round(16 * density);
            int content = AboveDock.contentWidth(grid, density);
            w = Math.max(1, (content - gap) / AboveDock.MAX_CARDS);
            cell = w; // gap entre cards via decoration na RecommendedRow; Continue usa margem no create
        } else {
            cell = inner / columns;
            w = 0;
            View dock = dockChild();
            if (dock != null) {
                View card = dock.findViewById(R.id.card);
                if (card != null) w = card.getWidth();
            }
            if (w <= 0) w = AppAdapter.tileWidthPx(inner, columns, density);
        }
        int h = Math.round(w * 9f / 16f);
        if (hosted) {
            int maxH = AboveDock.maxPosterHeight(grid, density);
            if (h > maxH) h = maxH; // largura permanece 2 colunas; thumb em centerCrop
        }
        if (w != tileW || cell != cellW || h != tileH) {
            tileW = w;
            tileH = h;
            cellW = cell;
            int side = hosted ? 0 : grid.getPaddingLeft();
            list.setPadding(side, 0, hosted ? 0 : grid.getPaddingRight(), 0);
            header.setPadding(side, 0, 0, 0);
            list.setAdapter(adapter);   // new holders at the new size
        }
        int height = Math.round(HEADER_DP * density) + itemHeight();
        if (!hosted) setGap(height);
        place();
    }

    private static final float HEADER_DP = 24, TOP_DP = 10, PAD_DP = 10, BOTTOM_DP = 8;

    private int itemHeight() {
        return tileH + Math.round((PAD_DP + BOTTOM_DP) * density);
    }

    private void setGap(int px) {
        int want = px == 0 ? 0 : px + Math.round(TOP_DP * density);
        if (want == gapPx) return;
        gapPx = want;
        grid.invalidateItemDecorations();
    }

    private View dockChild() {
        for (int i = 0; i < grid.getChildCount(); i++) {
            View c = grid.getChildAt(i);
            int pos = grid.getChildAdapterPosition(c);
            if (pos >= 0 && pos < columns) return c;
        }
        return null;
    }

    /** Puts the row on the gap under the dock row, wherever the grid has scrolled it. */
    private void place() {
        if (hosted || items.isEmpty() || gapPx == 0) return;
        View dock = dockChild();
        if (dock == null) {
            view.setTranslationY(-10000f);
            return;
        }
        RecyclerView.LayoutManager lm = grid.getLayoutManager();
        int bottom = lm == null ? dock.getBottom() : lm.getDecoratedBottom(dock);
        view.setTranslationY(grid.getTop() + bottom - gapPx + TOP_DP * density);
    }

    /** Scrolls the grid so the whole row is on screen. */
    private void reveal() {
        float top = view.getTranslationY();
        float bottom = top + view.getHeight();
        int limit = parent.getHeight() - Math.round(8 * density);
        int dy = 0;
        if (bottom > limit) dy = Math.round(bottom - limit);
        else if (top < 0) dy = Math.round(top);
        if (dy == 0) return;
        if (Apple.motion(animations) && !reduced) grid.smoothScrollBy(0, dy);
        else grid.scrollBy(0, dy);
    }

    final class Gap extends RecyclerView.ItemDecoration {
        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View child, @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
            int pos = parent.getChildAdapterPosition(child);
            if (gapPx > 0 && pos >= 0 && pos < columns) outRect.bottom += gapPx;
        }
    }

    // ---- focus -----------------------------------------------------------------------------------

    private int firstVisible() {
        LinearLayoutManager lm = (LinearLayoutManager) list.getLayoutManager();
        int first = lm == null ? 0 : lm.findFirstCompletelyVisibleItemPosition();
        return Math.max(0, first);
    }

    /** Down from the dock tile in this column: the card under it. */
    boolean focusColumn(int column) {
        if (!shown()) return false;
        return focusIndex(Math.min(items.size() - 1, firstVisible() + Math.max(0, column)));
    }

    boolean focusIndex(int index) {
        if (items.isEmpty()) return false;
        int target = Math.max(0, Math.min(items.size() - 1, index));
        RecyclerView.ViewHolder vh = list.findViewHolderForAdapterPosition(target);
        if (vh != null) return ((Holder) vh).card.requestFocus();
        list.scrollToPosition(target);
        list.post(() -> {
            RecyclerView.ViewHolder h = list.findViewHolderForAdapterPosition(target);
            if (h != null) ((Holder) h).card.requestFocus();
        });
        return true;
    }

    /** Last card in the row (used when arriving from Recommended via Left). */
    boolean focusLast() {
        return focusIndex(items.size() - 1);
    }

    private int columnOf(int index) {
        return Math.max(0, Math.min(columns - 1, index - firstVisible()));
    }

    // ---- actions ---------------------------------------------------------------------------------

    private void open(ContinueStore.Item it) {
        if (it.deepLink != null) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(it.deepLink)).setPackage(it.pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                activity.startActivity(i);
                return;
            } catch (Exception ignored) {
                // No activity of that app takes the link: open the app instead.
            }
        }
        PendingIntent session = ContinueStore.SOURCE_MEDIA.equals(it.source) ? ContinueListener.sessionActivity(it.pkg) : null;
        if (session != null) {
            try {
                session.send();
                return;
            } catch (Exception ignored) {
                // The session went away.
            }
        }
        PackageManager pm = activity.getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(it.pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(it.pkg);
        try {
            if (launch == null) throw new IllegalStateException();
            activity.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (Exception e) {
            Toast.makeText(activity, activity.getString(R.string.cant_open, it.title), Toast.LENGTH_SHORT).show();
        }
    }

    private void menu(ContinueStore.Item it) {
        new AlertDialog.Builder(activity).setTitle(it.title)
                .setItems(new CharSequence[]{activity.getString(R.string.cw_remove)}, (d, which) -> {
                    Context app = activity.getApplicationContext();
                    work.execute(() -> ContinueProvider.remove(app, it.pkg, it.id));
                }).show();
    }

    // ---- cards -----------------------------------------------------------------------------------

    final class Holder extends RecyclerView.ViewHolder {
        final FrameLayout card;
        final ImageView image, placeholderIcon;
        final View scrim, bar;
        final ProgressBarDrawable progress = new ProgressBarDrawable();
        final TextView title, app;
        final ImageView appIcon;
        final AppleFocus focus;
        String imageKey;

        Holder(FrameLayout item, FrameLayout card, ImageView image, ImageView placeholderIcon, View scrim, View bar,
               TextView title, ImageView appIcon, TextView app, AppleFocus focus) {
            super(item);
            this.card = card;
            this.image = image;
            this.placeholderIcon = placeholderIcon;
            this.scrim = scrim;
            this.bar = bar;
            this.title = title;
            this.appIcon = appIcon;
            this.app = app;
            this.focus = focus;
            bar.setBackground(progress);
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
        // Shelf cards: tvOS-like radius (≥20dp), not the tighter dock tile radius.
        float radius = Math.min(28f * density, Math.max(18f * density, w * 0.045f));
        View shadow = new View(ctx);
        shadow.setBackground(SoftShadow.drawable(ctx.getResources(), w, h, radius));
        shadow.setAlpha(0f);
        shadow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(w + 2 * pad, h + 2 * pad,
                Gravity.TOP | Gravity.START);
        slp.leftMargin = -pad;
        slp.topMargin = -pad;
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
        item.addView(card, new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.START));

        ImageView placeholderIcon = new ImageView(ctx);
        placeholderIcon.setAlpha(0.35f);
        card.addView(placeholderIcon, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
        ImageView image = new ImageView(ctx);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        // Scrim always on: title overlays artwork; contrast target ≥4.5:1 (HIG color / UX).
        View scrim = new View(ctx);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x33000000, 0xCC000000}));
        card.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(Math.round(h * 0.48f), Math.round(56 * density)), Gravity.BOTTOM));
        GlareView glare = new GlareView(ctx);
        glare.setAlpha(0f);
        card.addView(glare, new FrameLayout.LayoutParams(2 * w, 2 * h, Gravity.CENTER));
        glare.setTileSize(w, h);

        // Title and app on the artwork (tvOS shelf): the row stays one tile tall.
        LinearLayout textBlock = new LinearLayout(ctx);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(ctx);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(0f, 1.05f);
        Apple.style(title, Apple.MEDIUM, Apple.TEXT, density);
        textBlock.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout appRow = new LinearLayout(ctx);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView appIcon = new ImageView(ctx);
        appIcon.setVisibility(View.GONE);
        appRow.addView(appIcon, new LinearLayout.LayoutParams(0, 0));
        TextView app = new TextView(ctx);
        app.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        app.setSingleLine(true);
        app.setEllipsize(TextUtils.TruncateAt.END);
        Apple.style(app, Apple.REGULAR, 0xE6FFFFFF, density);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        appRow.addView(app, alp);
        LinearLayout.LayoutParams appLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        appLp.topMargin = dp(4);
        textBlock.addView(appRow, appLp);
        FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        textLp.leftMargin = dp(16);
        textLp.rightMargin = dp(16);
        textLp.bottomMargin = dp(18);
        card.addView(textBlock, textLp);

        // Full-bleed progress on the poster edge — does not compete with the title.
        View bar = new View(ctx);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3),
                Gravity.BOTTOM);
        card.addView(bar, blp);

        AppleFocus focus = new AppleFocus(item, card, shadow, glare, animations, parallax, reduced);
        focus.setTileSize(w, h);
        Holder holder = new Holder(item, card, image, placeholderIcon, scrim, bar, title, appIcon, app, focus);
        title.setAlpha(0.92f);
        card.setOnFocusChangeListener((v, has) -> {
            focus.onFocusChanged(has);
            float a = has ? 1f : 0.92f;
            if (Apple.motion(animations)) title.animate().alpha(a).setDuration(150).start();
            else title.setAlpha(a);
            if (has) {
                host.onRowFocused();
                if (!hosted) reveal();
            }
        });
        card.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) open(items.get(pos));
        });
        card.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) menu(items.get(pos));
            return true;
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
                    // Edge: ask AboveDock to switch section; always keep the key so focus does not escape.
                    if (left && pos == 0 || !left && pos == items.size() - 1) {
                        host.onRowSide(!left);
                        return true;
                    }
                    focusIndex(pos + (left ? -1 : 1));
                    return true;
                default:
                    return false;
            }
        });
        return holder;
    }

    private void bind(Holder h, ContinueStore.Item it) {
        h.title.setText(it.title);
        String label = labels.get(it.pkg);
        h.app.setText(it.subtitle != null ? it.subtitle : label);
        Drawable icon = icons.get(it.pkg);
        h.appIcon.setImageDrawable(icon);
        h.placeholderIcon.setImageDrawable(icon);
        boolean hasProgress = it.progress > 0.005f;
        h.scrim.setVisibility(View.VISIBLE);
        h.bar.setVisibility(hasProgress ? View.VISIBLE : View.GONE);
        h.progress.setProgress(it.progress);
        String desc = it.title + (label == null ? "" : ", " + label);
        if (hasProgress) desc += ", " + Math.round(it.progress * 100f) + "%";
        h.card.setContentDescription(desc);
        h.focus.snap(h.card.isFocused());
        String key = ContinueImages.keyOf(it);
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
        if (key == null) return;
        ContinueImages.load(activity, key, tileW, b -> {
            if (b == null || !key.equals(h.imageKey)) return;
            h.image.setImageBitmap(b);
            h.placeholderIcon.setVisibility(View.INVISIBLE);
            if (Apple.motion(animations) && !reduced) h.image.animate().alpha(1f).setDuration(220).start();
            else h.image.setAlpha(1f);
        });
    }

    /** The thin progress line: #F5F5F7 over #33FFFFFF, fully rounded. */
    static final class ProgressBarDrawable extends Drawable {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float progress;

        ProgressBarDrawable() {
            track.setColor(0x33FFFFFF);
            fill.setColor(Apple.TEXT);
        }

        void setProgress(float p) {
            progress = Math.max(0f, Math.min(1f, p));
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas c) {
            Rect b = getBounds();
            float radius = b.height() / 2f;
            r.set(b);
            c.drawRoundRect(r, radius, radius, track);
            float end = b.left + Math.max(b.height(), b.width() * progress);
            r.set(b.left, b.top, end, b.bottom);
            c.drawRoundRect(r, radius, radius, fill);
        }

        @Override
        public void setAlpha(int alpha) {
            track.setAlpha(alpha * 0x33 / 255);
            fill.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            fill.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
