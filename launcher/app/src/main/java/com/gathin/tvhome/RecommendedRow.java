package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Rect;
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
 * Linha Recomendados da Home (recommended_row): cards 16:9 da prateleira Recomendados da conta
 * do YouTube. AboveDock posiciona a linha acima da dock; OK abre o vídeo no YouTube TV.
 * Sem sessão, a linha mostra o código para vincular a conta.
 */
final class RecommendedRow {

    private static final String YOUTUBE_TV = MicSearch.YOUTUBE_TV;
    private static final float PAD_DP = 4;

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
    private final LinearLayout linkBox;
    private final TextView linkCodeView;
    private final RowAdapter adapter = new RowAdapter();
    private boolean linking;
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
        list.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect out, @NonNull View view,
                                       @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int pos = parent.getChildAdapterPosition(view);
                if (pos >= 0 && pos < items.size() - 1) out.right = Math.round(20 * density);
            }
        });
        view.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        linkBox = new LinearLayout(activity);
        linkBox.setOrientation(LinearLayout.VERTICAL);
        linkBox.setFocusable(true);
        linkBox.setVisibility(View.GONE);
        linkCodeView = new TextView(activity);
        linkCodeView.setTextColor(Apple.TEXT);
        linkCodeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        linkCodeView.setTypeface(Apple.MEDIUM);
        linkCodeView.setLetterSpacing(0.18f);
        TextView linkHint = new TextView(activity);
        linkHint.setTextColor(Apple.TEXT_2);
        linkHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        linkHint.setTypeface(Apple.REGULAR);
        linkBox.addView(linkCodeView);
        linkBox.addView(linkHint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        view.addView(linkBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        linkBox.setTag(linkHint);

        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> grid.post(this::measure));
    }

    void start() {
        if (started) return;
        started = true;
        load();
    }

    void stop() {
        started = false;
        RecommendedProvider.cancel();
    }

    void destroy() {
        stop();
    }

    boolean shown() {
        return (!items.isEmpty() || linking) && view.getVisibility() == View.VISIBLE;
    }

    /** True while there is at least one item, whether or not AboveDock is showing this page now. */
    boolean hasItems() {
        return !items.isEmpty() || linking;
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
        RecommendedProvider.fetch(activity, result -> {
            if (activity.isDestroyed()) return;
            if (result.linkCode != null) {
                showLink(result.linkCode, result.linkUrl);
                return;
            }
            linking = false;
            linkBox.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
            apply(result.items);
        });
    }

    private void showLink(String code, String url) {
        linking = true;
        items.clear();
        adapter.notifyDataSetChanged();
        linkCodeView.setText(code);
        TextView hint = (TextView) linkBox.getTag();
        hint.setText(activity.getString(R.string.rec_link_hint, url));
        list.setVisibility(View.GONE);
        linkBox.setVisibility(View.VISIBLE);
        view.setVisibility(View.VISIBLE);
        if (onChanged != null) onChanged.run();
    }

    private void apply(List<RecommendedProvider.Item> fresh) {
        View focused = list.getFocusedChild();
        int focusIndex = focused == null ? -1 : list.getChildAdapterPosition(focused);
        items.clear();
        int n = Math.min(AboveDock.MAX_CARDS, fresh.size());
        for (int i = 0; i < n; i++) items.add(fresh.get(i));
        if (!icons.containsKey(YOUTUBE_TV)) {
            try {
                ApplicationInfo ai = activity.getPackageManager().getApplicationInfo(YOUTUBE_TV, 0);
                icons.put(YOUTUBE_TV, ai.loadIcon(activity.getPackageManager()));
                labels.put(YOUTUBE_TV, ai.loadLabel(activity.getPackageManager()).toString());
            } catch (Exception ignored) {
                icons.put(YOUTUBE_TV, null);
                labels.put(YOUTUBE_TV, "YouTube");
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

    /** Exatamente 2 posters no container; o gap entre eles vem do ItemDecoration. */
    private void measure() {
        if ((items.isEmpty() && !linking) || grid.getWidth() == 0) return;
        int inner = AboveDock.contentWidth(grid, density);
        int gap = Math.round(20 * density);
        int maxH = AboveDock.maxPosterHeight(grid, density);
        int w = Math.max(1, (inner - gap) / AboveDock.MAX_CARDS);
        int h = Math.min(Math.round(w * 9f / 16f), maxH);
        if (w != tileW || h != tileH || cellW != w) {
            tileW = w;
            tileH = h;
            cellW = w;
            list.setPadding(0, 0, 0, 0);
            list.setAdapter(adapter);   // new holders at the new size
        }
    }

    private int itemHeight() {
        return tileH + Math.round((PAD_DP + AboveDock.LABEL_DP) * density);
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
        if (linking && items.isEmpty()) return linkBox.requestFocus();
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

    /** Last card in the row (used when arriving from Continue via Left). */
    boolean focusLast() {
        return focusIndex(items.size() - 1);
    }

    private int columnOf(int index) {
        return Math.max(0, Math.min(columns - 1, index));
    }

    // ---- actions ---------------------------------------------------------------------------------

    private void open(RecommendedProvider.Item it) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + it.videoId))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (icons.containsKey(YOUTUBE_TV) && icons.get(YOUTUBE_TV) != null) {
            i.setPackage(YOUTUBE_TV);
        }
        try {
            activity.startActivity(i);
            return;
        } catch (Exception ignored) {
            // YouTube TV did not take the link: open the app.
        }
        PackageManager pm = activity.getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(YOUTUBE_TV);
        if (launch == null) launch = pm.getLaunchIntentForPackage(YOUTUBE_TV);
        try {
            if (launch == null) throw new IllegalStateException();
            activity.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (Exception e) {
            Toast.makeText(activity, activity.getString(R.string.cant_open, labels.get(YOUTUBE_TV)),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ---- cards -----------------------------------------------------------------------------------

    final class Holder extends RecyclerView.ViewHolder {
        final FrameLayout card;
        final ImageView image, placeholderIcon;
        final TextView title, meta, duration;
        final AppleFocus focus;
        String imageKey;

        Holder(View item, FrameLayout card, ImageView image, ImageView placeholderIcon,
               TextView title, TextView meta, TextView duration, AppleFocus focus) {
            super(item);
            this.card = card;
            this.image = image;
            this.placeholderIcon = placeholderIcon;
            this.title = title;
            this.meta = meta;
            this.duration = duration;
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
        LinearLayout item = new LinearLayout(ctx);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setClipChildren(false);
        item.setClipToPadding(false);
        item.setPadding(0, dp(PAD_DP), 0, 0);
        item.setLayoutParams(new RecyclerView.LayoutParams(w, itemHeight()));

        FrameLayout art = new FrameLayout(ctx);
        art.setClipChildren(false);
        art.setClipToPadding(false);
        item.addView(art, new LinearLayout.LayoutParams(w, h));

        int pad = dp(SoftShadow.PAD_DP);
        float radius = Math.min(22f * density, Math.max(16f * density, w * 0.04f));
        View shadow = new View(ctx);
        shadow.setBackground(SoftShadow.drawable(ctx.getResources(), w, h, radius));
        shadow.setAlpha(0f);
        shadow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(w + 2 * pad, h + 2 * pad,
                Gravity.TOP | Gravity.START);
        slp.leftMargin = -pad;
        slp.topMargin = -pad;
        art.addView(shadow, slp);

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
        art.addView(card, new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.START));

        ImageView placeholderIcon = new ImageView(ctx);
        placeholderIcon.setAlpha(0.35f);
        card.addView(placeholderIcon, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
        ImageView image = new ImageView(ctx);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView duration = new TextView(ctx);
        duration.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        duration.setSingleLine(true);
        duration.setPadding(dp(8), dp(3), dp(8), dp(3));
        Apple.style(duration, Apple.MEDIUM, Apple.TEXT, density);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xCC000000);
        pill.setCornerRadius(8f * density);
        duration.setBackground(pill);
        FrameLayout.LayoutParams durLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        durLp.setMargins(0, 0, dp(10), dp(10));
        card.addView(duration, durLp);

        GlareView glare = new GlareView(ctx);
        glare.setAlpha(0f);
        card.addView(glare, new FrameLayout.LayoutParams(2 * w, 2 * h, Gravity.CENTER));
        glare.setTileSize(w, h);

        // Label sob o poster (como o nome sob o ícone da dock): evita texto sobre a arte.
        TextView title = new TextView(ctx);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(0f, 1.1f);
        Apple.style(title, Apple.MEDIUM, Apple.TEXT, density);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(8);
        item.addView(title, titleLp);

        TextView meta = new TextView(ctx);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        Apple.style(meta, Apple.REGULAR, Apple.TEXT_2, density);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = dp(2);
        item.addView(meta, metaLp);

        AppleFocus focus = new AppleFocus(art, card, shadow, glare, animations, parallax, reduced);
        focus.setTileSize(w, h);
        Holder holder = new Holder(item, card, image, placeholderIcon, title, meta, duration, focus);
        card.setOnFocusChangeListener((v, has) -> {
            focus.onFocusChanged(has);
            float a = has ? 1f : 0.9f;
            if (Apple.motion(animations)) {
                title.animate().alpha(a).setDuration(150).start();
                meta.animate().alpha(a).setDuration(150).start();
            } else {
                title.setAlpha(a);
                meta.setAlpha(a);
            }
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

    private void bind(Holder h, RecommendedProvider.Item it) {
        h.title.setText(it.title);
        Drawable icon = icons.get(YOUTUBE_TV);
        h.placeholderIcon.setImageDrawable(icon);
        String channel = it.channel != null ? it.channel : labels.get(YOUTUBE_TV);
        h.meta.setText(channel);
        String durationLabel = formatDuration(it.durationSec);
        if (durationLabel == null) {
            h.duration.setVisibility(View.GONE);
        } else {
            h.duration.setVisibility(View.VISIBLE);
            h.duration.setText(durationLabel);
        }
        String channelDesc = channel == null ? "" : ", " + channel;
        String durationDesc = durationLabel == null ? "" : ", " + durationLabel;
        h.card.setContentDescription(it.title + channelDesc + durationDesc);
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

    /** m:ss or h:mm:ss; null when the provider sent no useful length. */
    private static String formatDuration(int sec) {
        if (sec <= 0) return null;
        int h = sec / 3600;
        int m = (sec % 3600) / 60;
        int s = sec % 60;
        if (h > 0) return h + ":" + two(m) + ":" + two(s);
        return m + ":" + two(s);
    }

    private static String two(int n) {
        return n < 10 ? "0" + n : Integer.toString(n);
    }
}
