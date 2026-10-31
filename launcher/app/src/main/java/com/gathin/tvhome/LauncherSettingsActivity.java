package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.tvhome.SettingsCatalog.SettingRow;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The launcher's Settings, laid out like the Apple TV's: a large preview of the focused row on the left, the
 * screen's rows on the right, every row from {@link SettingsCatalog}. A change is saved to launcher_settings at
 * once; RESULT_OK makes Home rebuild with it when Settings closes. D-pad only: OK changes or opens, Back returns
 * to the row the screen was opened from.
 */
public final class LauncherSettingsActivity extends Activity {
    private static final int TEXT = 0xFFF5F5F7;
    private static final int TEXT_2 = 0xFFA1A1A6;
    private static final String LICENSE = "MIT";
    private static final String PROJECT = "github.com/Gathin23/cleanyourtv";
    /** Seconds; -1 follows the theme. 5 s is there for a quick test. */
    private static final int[] ROTATION_PRESETS = {-1, 0, 5, 5 * 60, 15 * 60, 30 * 60, 60 * 60, 120 * 60};
    private static final int[] OPACITY_PRESETS = {30, 55, 70, 85};

    /** One line on screen: a catalog row, or (label set) one option of the CHOICE row. */
    private static final class Item {
        final SettingRow row;
        final String label;
        final Object value;
        /** The 4K source this line stands for (SOURCES screen), else null. */
        final String source;

        Item(SettingRow row, String label, Object value) {
            this(row, label, value, null);
        }

        Item(SettingRow row, String label, Object value, String source) {
            this.row = row;
            this.label = label;
            this.value = value;
            this.source = source;
        }
    }

    private SharedPreferences choices;
    private Config config;
    private WallpaperSources.Keys keys;
    /** Position of the source being moved with the arrows (long press on OK), or -1. */
    private int grabbed = -1;
    /** The key whose release must not reach the row, because its press already ended the move. */
    private int swallowUpKey = -1;
    private float dp;
    /** False when the TV's animator scale is off: every change then snaps. */
    private boolean motion;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newSingleThreadExecutor();

    /** From ROOT to the screen shown, and for each the position it was opened from. */
    private final ArrayList<SettingRow> path = new ArrayList<>();
    private final ArrayList<Integer> origins = new ArrayList<>();
    private final ArrayList<Item> items = new ArrayList<>();
    /** Wallpaper option value (a file name) to the file, for its thumbnail. */
    private final Map<String, File> wallpaperFiles = new HashMap<>();
    private final Map<File, Bitmap> thumbs = new HashMap<>();

    private ImageView backdrop;
    private ImageView artFront;
    private ImageView artBack;
    private Object artShown;
    private TextView description;
    private TextView soon;
    private LinearLayout column;
    private TextView title;
    private RecyclerView list;
    private final Rows adapter = new Rows();
    private boolean moving;
    /** The position to focus once the list has laid out its new rows; -1 for none. */
    private int pendingFocus = -1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        motion = ValueAnimator.areAnimatorsEnabled();
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        config = Config.load(this);
        keys = SourcesWallpaper.keys(this);
        setContentView(buildLayout());
        path.add(SettingsCatalog.ROOT);
        origins.add(0);
        fill(0);
        loadBackdrop();
    }

    @Override
    protected void onDestroy() {
        work.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (moving) return;
        if (path.size() > 1) back();
        else super.onBackPressed();
    }

    // ---- layout ----------------------------------------------------------------------------------

    private View buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF2C2C30, 0xFF0A0A0C}));
        root.addView(backdrop, match());
        View scrim = new View(this);
        // Darker behind the list than behind the preview, as on tvOS; the blurred picture still shows through.
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x8C000000, 0xB3000000, 0xCC000000}));
        root.addView(scrim, match());

        int safe = (int) (48 * dp);
        LinearLayout columns = new LinearLayout(this);
        columns.setPadding(safe, (int) (36 * dp), safe, (int) (28 * dp));
        root.addView(columns, match());

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(0, 0, (int) (40 * dp), 0);
        columns.addView(preview, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 4));
        FrameLayout art = new FrameLayout(this);
        artFront = artView();
        artBack = artView();
        artBack.setAlpha(0f);
        art.addView(artBack, match());
        art.addView(artFront, match());
        preview.addView(art, new LinearLayout.LayoutParams((int) (320 * dp), (int) (180 * dp)));
        description = text(17, TEXT_2);
        description.setGravity(Gravity.CENTER_HORIZONTAL);
        description.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams((int) (300 * dp), ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = (int) (28 * dp);
        preview.addView(description, dlp);
        soon = text(13, TEXT);
        soon.setPadding((int) (12 * dp), (int) (4 * dp), (int) (12 * dp), (int) (5 * dp));
        soon.setBackground(round(0x26FFFFFF, 12 * dp));
        soon.setText(R.string.set_soon);
        soon.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams slp = wrap();
        slp.topMargin = (int) (16 * dp);
        preview.addView(soon, slp);

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        columns.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 6));
        title = text(34, TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setMaxLines(1);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding((int) (32 * dp), 0, (int) (32 * dp), 0);
        // Long section names (Portuguese ones especially) shrink to fit on one line instead of being cut.
        title.setAutoSizeTextTypeUniformWithConfiguration(22, 34, 1, TypedValue.COMPLEX_UNIT_SP);
        column.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (52 * dp)));
        list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this) {
            /** Focus scrolls the list early, so the focused row stays clear of the faded edges, as on tvOS. */
            @Override
            public boolean requestChildRectangleOnScreen(RecyclerView parent, View child, Rect rect,
                                                         boolean immediate, boolean focusedChildVisible) {
                int margin = (int) (64 * dp);
                int top = child.getTop() - margin;
                int bottom = child.getBottom() + margin - parent.getHeight();
                int dy = top < 0 ? top : Math.max(0, Math.min(bottom, top));
                if (dy == 0) return false;
                if (immediate || !motion) parent.scrollBy(0, dy);
                else parent.smoothScrollBy(0, dy);
                return true;
            }
        });
        list.setItemAnimator(null);
        list.setAdapter(adapter);
        // Rows end at the list's edges, which fade while there is more to scroll to.
        list.setVerticalFadingEdgeEnabled(true);
        list.setFadingEdgeLength((int) (40 * dp));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        llp.topMargin = (int) (12 * dp);
        column.addView(list, llp);
        return root;
    }

    private ImageView artView() {
        ImageView v = new ImageView(this);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 14 * dp);
            }
        });
        v.setClipToOutline(true);
        return v;
    }

    private TextView text(int sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // ---- screens ---------------------------------------------------------------------------------

    private SettingRow screen() {
        return path.get(path.size() - 1);
    }

    /** Puts the current screen's rows on the list and focuses the one at {@code focus}. */
    @SuppressLint("NotifyDataSetChanged")
    private void fill(int focus) {
        SettingRow screen = screen();
        items.clear();
        grabbed = -1;
        swallowUpKey = -1;
        if (screen.type == SettingsCatalog.SOURCES) {
            for (String id : WallpaperSources.order(choices.getString(WallpaperSources.ORDER_KEY, null))) {
                items.add(new Item(screen, null, null, id));
            }
            if (focus < 0) focus = 0;
        } else if (screen.type == SettingsCatalog.CHOICE) {
            items.addAll(options(screen));
            if (focus < 0) {
                focus = 0;
                for (int i = 0; i < items.size(); i++) if (checked(items.get(i))) focus = i;
            }
        } else {
            for (SettingRow row : screen.children) items.add(new Item(row, null, null));
        }
        title.setText(screen.title);
        pendingFocus = Math.max(0, Math.min(focus, items.size() - 1));
        adapter.notifyDataSetChanged();
        list.scrollToPosition(pendingFocus);
    }

    /** A section or a choice: the new screen slides in from the right. */
    private void open(SettingRow row, int from) {
        path.add(row);
        origins.add(from);
        fill(-1);
        if (!motion) return;
        column.animate().cancel();
        column.setTranslationX(72 * dp);
        column.setAlpha(0f);
        moving = true;
        column.animate().translationX(0f).alpha(1f).setDuration(250)
                .setInterpolator(new DecelerateInterpolator(2f)).withEndAction(() -> moving = false).start();
    }

    /** Back: the screen leaves to the right, faster, and focus returns to the row that opened it. */
    private void back() {
        int from = origins.remove(origins.size() - 1);
        path.remove(path.size() - 1);
        if (!motion) {
            fill(from);
            return;
        }
        moving = true;
        column.animate().cancel();
        column.animate().translationX(72 * dp).alpha(0f).setDuration(180)
                .setInterpolator(new AccelerateInterpolator()).withEndAction(() -> {
                    fill(from);
                    column.setTranslationX(-24 * dp);
                    column.animate().translationX(0f).alpha(1f).setDuration(200)
                            .setInterpolator(new DecelerateInterpolator(2f)).withEndAction(() -> moving = false).start();
                }).start();
    }

    // ---- values ----------------------------------------------------------------------------------

    /** Advanced effects cannot be on without animations: the row is dimmed and OK does nothing. */
    private boolean dim(Item it) {
        if (it.source != null) return WallpaperSources.unavailable(it.source, keys);
        return it.label == null && Config.FEATURE_ADVANCED_EFFECTS.equals(it.row.key) && !isOn(Config.FEATURE_ANIMATIONS);
    }

    private boolean isOn(String key) {
        if (Config.FEATURE_ADVANCED_EFFECTS.equals(key) && !isOn(Config.FEATURE_ANIMATIONS)) return false;
        return choices.contains(key) ? choices.getBoolean(key, false) : config.feature(key);
    }

    private boolean sourceOn(String id) {
        return WallpaperSources.enabled(id, choices.getString(WallpaperSources.STATE_KEY, null), keys);
    }

    /** The grey text at the end of a row. */
    private String valueOf(Item it) {
        SettingRow row = it.row;
        if (it.source != null) {
            if (grabbed >= 0 && grabbed < items.size() && items.get(grabbed) == it) return getString(R.string.set_src_moving);
            if (WallpaperSources.unavailable(it.source, keys)) return getString(R.string.set_src_nokey);
            return getString(sourceOn(it.source) ? R.string.on : R.string.off);
        }
        if (it.label != null) return checked(it) ? "✓" : "";
        switch (row.type) {
            case SettingsCatalog.BOOL:
                return getString(isOn(row.key) ? R.string.on : R.string.off);
            case SettingsCatalog.SOURCES:
                return String.valueOf(WallpaperSources.active(choices.getString(WallpaperSources.ORDER_KEY, null),
                        choices.getString(WallpaperSources.STATE_KEY, null), keys).size());
            case SettingsCatalog.CHOICE:
                for (Item option : options(row)) if (checked(option)) return option.label;
                if (SettingsCatalog.WALLPAPER.equals(row.key)) return choices.getString(row.key, "");
                return "";
            case SettingsCatalog.ACTION:
                if (SettingsCatalog.VERSION.equals(row.key)) return versionName();
                if (SettingsCatalog.LICENSE.equals(row.key)) return LICENSE;
                if (SettingsCatalog.PROJECT.equals(row.key)) return PROJECT;
                if (SettingsCatalog.FOLLOW_THEME.equals(row.key)) return hasOnTvChoices() ? "" : "\u2713";
                return SystemActions.value(this, row.key);
            default:
                return "";
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    /** A CHOICE row's options, labels in the order the old picker had them. */
    private List<Item> options(SettingRow row) {
        List<Item> out = new ArrayList<>();
        String key = row.key;
        if (SettingsCatalog.WALLPAPER.equals(key)) {
            out.add(new Item(row, getString(R.string.wallpaper_theme_default), null));
            File dir = Config.dir(this);
            List<File> images = new ArrayList<>(WallpaperRotation.images(dir == null ? null : new File(dir, "wallpapers")));
            // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
            File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
            if (single != null && single.isFile()) images.add(0, single);
            wallpaperFiles.clear();
            for (File f : images) {
                wallpaperFiles.put(f.getName(), f);
                out.add(new Item(row, f.getName(), f.getName()));
            }
            if (isOn(Config.FEATURE_BING)) out.add(new Item(row, getString(R.string.wallpaper_bing), BingWallpaper.CHOICE));
            out.add(new Item(row, getString(R.string.set_sources), SourcesWallpaper.CHOICE));
            String url = getString(R.string.wallpaper_from_url);
            if (WallpaperUrls.CHOICE.equals(choices.getString(key, null))) {
                url = getString(R.string.wallpaper_url_state, WallpaperUrls.parse(WallpaperUrls.saved(this)).size());
            }
            out.add(new Item(row, url, WallpaperUrls.CHOICE));
            out.add(new Item(row, getString(R.string.wallpaper_none), "none"));
        } else if (SettingsCatalog.ROTATION.equals(key)) {
            for (int s : ROTATION_PRESETS) {
                String label = s < 0 ? getString(R.string.wallpaper_theme_default)
                        : s == 0 ? getString(R.string.wallpaper_rotation_off)
                        : s < 60 ? getString(R.string.wallpaper_rotation_seconds, s)
                        : getString(R.string.wallpaper_rotation_minutes, s / 60);
                out.add(new Item(row, label, s));
            }
        } else if (SettingsCatalog.TRANSITION.equals(key)) {
            out.add(new Item(row, getString(R.string.wallpaper_transition_none), Config.TRANSITION_NONE));
            out.add(new Item(row, getString(R.string.wallpaper_transition_crossfade), Config.TRANSITION_CROSSFADE));
        } else if (SettingsCatalog.OPACITY.equals(key)) {
            for (int o : OPACITY_PRESETS) out.add(new Item(row, o + "%", o));
        } else if (SettingsCatalog.COLUMNS.equals(key)) {
            for (int c = 4; c <= 7; c++) out.add(new Item(row, String.valueOf(c), c));
        } else if (SettingsCatalog.ROWS.equals(key)) {
            for (int r = 0; r <= 3; r++) out.add(new Item(row, String.valueOf(r), r));
        }
        return out;
    }

    private boolean checked(Item option) {
        String key = option.row.key;
        Object current;
        if (SettingsCatalog.WALLPAPER.equals(key)) {
            String choice = choices.getString(key, null);
            boolean off = BingWallpaper.CHOICE.equals(choice) && !isOn(Config.FEATURE_BING)
                    || SourcesWallpaper.CHOICE.equals(choice) && !isOn(SourcesWallpaper.FEATURE);
            current = off ? null : choice;
        } else if (SettingsCatalog.ROTATION.equals(key)) {
            current = choices.contains(key) ? choices.getInt(key, 0) : -1;
        } else if (SettingsCatalog.TRANSITION.equals(key)) {
            current = Config.normalizeWallpaperTransition(choices.getString(key, Config.TRANSITION_NONE));
        } else if (SettingsCatalog.OPACITY.equals(key)) {
            current = config.dockGlassOpacity;
        } else if (SettingsCatalog.COLUMNS.equals(key)) {
            current = config.columns;
        } else {
            current = config.rowsBelowDock;
        }
        return Objects.equals(current, option.value);
    }

    // ---- changes ---------------------------------------------------------------------------------

    private void click(int position) {
        if (moving || position < 0 || position >= items.size()) return;
        Item it = items.get(position);
        SettingRow row = it.row;
        if (it.source != null) {
            toggleSource(it.source);
            return;
        }
        if (it.label != null) {
            pick(it);
            return;
        }
        switch (row.type) {
            case SettingsCatalog.SUBSCREEN:
            case SettingsCatalog.CHOICE:
            case SettingsCatalog.SOURCES:
                open(row, position);
                break;
            case SettingsCatalog.BOOL:
                if (dim(it)) return;
                // Com PIN definido, ligar ou desligar o controle parental pede o PIN.
                if (SystemActions.guard(this, row.key, () -> toggle(row))) break;
                toggle(row);
                break;
            default:
                action(row.key);
        }
    }

    private void toggle(SettingRow row) {
        boolean on = !isOn(row.key);
        SharedPreferences.Editor edit = choices.edit().putBoolean(row.key, on);
        // Advanced effects need animations; switching those off switches both off, as before.
        if (Config.FEATURE_ANIMATIONS.equals(row.key) && !on) edit.putBoolean(Config.FEATURE_ADVANCED_EFFECTS, false);
        boolean sourcesKey = SourcesWallpaper.FEATURE.equals(row.key);
        // A new search on the next Home start picks up the change.
        if (sourcesKey || "seasonal_wallpaper".equals(row.key)) edit.remove(SourcesWallpaper.DATE_KEY);
        edit.apply();
        if (sourcesKey && !on) SourcesWallpaper.deleteCache(this);
        // Bing or the 4K sources off drop their wallpaper, so the preview and backdrop need the new Config.
        changed(Config.FEATURE_BING.equals(row.key) || sourcesKey);
        SystemActions.toggled(this, row.key, on, () -> changed(false));
    }

    private void pick(Item option) {
        String key = option.row.key;
        Object v = option.value;
        SharedPreferences.Editor edit = choices.edit();
        if (SettingsCatalog.WALLPAPER.equals(key)) {
            if (WallpaperUrls.CHOICE.equals(v)) {
                showUrlEditor();
                return;
            }
            if (v == null) {
                edit.remove(key).remove(WallpaperUrls.URLS_KEY).apply();
                WallpaperUrls.deleteCache(this);
            } else if (SourcesWallpaper.CHOICE.equals(v)) {
                // Picking the 4K sources switches the function on and drops the other remote cache.
                edit.putString(key, (String) v).putBoolean(SourcesWallpaper.FEATURE, true)
                        .remove(WallpaperUrls.URLS_KEY).remove(SourcesWallpaper.DATE_KEY).apply();
                WallpaperUrls.deleteCache(this);
            } else {
                edit.putString(key, (String) v).apply();
            }
            if (!SourcesWallpaper.CHOICE.equals(v)) SourcesWallpaper.deleteCache(this);
            if (BingWallpaper.CHOICE.equals(v)) download(BingWallpaper::fetchAndCache);
            else if (SourcesWallpaper.CHOICE.equals(v)) download(SourcesWallpaper::fetchAndCache);
            else Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
            changed(true);
        } else {
            if (SettingsCatalog.ROTATION.equals(key) && (Integer) v < 0) edit.remove(key);
            else if (SettingsCatalog.TRANSITION.equals(key) && Config.TRANSITION_NONE.equals(v)) edit.remove(key);
            else if (v instanceof Integer) edit.putInt(key, (Integer) v);
            else edit.putString(key, (String) v);
            edit.apply();
            changed(true);
        }
        // Like a tvOS choice list: the tick moves, then the list closes back onto its row.
        handler.postDelayed(() -> {
            if (!isFinishing() && screen() == option.row) back();
        }, motion ? 260 : 0);
    }

    /** Something was saved: Home rebuilds on return, and the rows on screen show the new values. */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Backup importado: as escolhas e o config.json mudaram.
        if (requestCode == SystemActions.REQUEST_BACKUP && resultCode == RESULT_OK) changed(true);
    }

    private void changed(boolean reload) {
        setResult(RESULT_OK);
        if (reload) config = Config.load(this);
        refreshRows();
        if (reload) {
            artShown = null;
            View f = list.getFocusedChild();
            if (f != null) preview(items.get(Math.max(0, list.getChildAdapterPosition(f))));
            loadBackdrop();
        }
    }

    /** The visible rows show their current value again. */
    private void refreshRows() {
        for (int i = 0; i < list.getChildCount(); i++) {
            RecyclerView.ViewHolder h = list.getChildViewHolder(list.getChildAt(i));
            int p = h.getBindingAdapterPosition();
            if (p < 0 || p >= items.size()) continue;
            ((Rows.Holder) h).value(valueOf(items.get(p)), true);
            h.itemView.setAlpha(dim(items.get(p)) ? 0.45f : 1f);
        }
    }

    /** OK on a source: on or off. A source without its key cannot be switched on. */
    private void toggleSource(String id) {
        if (WallpaperSources.unavailable(id, keys)) {
            Toast.makeText(this, getString(R.string.set_src_key_d, WallpaperSources.keyName(id)), Toast.LENGTH_LONG).show();
            return;
        }
        String states = WallpaperSources.withState(choices.getString(WallpaperSources.STATE_KEY, null), id, !sourceOn(id));
        choices.edit().putString(WallpaperSources.STATE_KEY, states).remove(SourcesWallpaper.DATE_KEY).apply();
        changed(false);
    }

    /** Long press on OK: the source follows the up and down keys until OK or Back. */
    private boolean grab(int position) {
        if (position < 0 || position >= items.size() || items.get(position).source == null) return false;
        grabbed = position;
        refreshRows();
        return true;
    }

    private void moveGrabbed(int delta) {
        int from = grabbed;
        int to = from + delta;
        if (from < 0 || to < 0 || to >= items.size()) return;
        items.add(to, items.remove(from));
        adapter.notifyItemMoved(from, to);
        grabbed = to;
        List<String> order = new ArrayList<>();
        for (Item item : items) order.add(item.source);
        choices.edit().putString(WallpaperSources.ORDER_KEY, WallpaperSources.joinOrder(order))
                .remove(SourcesWallpaper.DATE_KEY).apply();
        setResult(RESULT_OK);
        list.scrollToPosition(to);
        list.post(() -> {
            RecyclerView.ViewHolder h = list.findViewHolderForAdapterPosition(to);
            if (h != null) h.itemView.requestFocus();
            refreshRows();
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_UP && code == swallowUpKey) {
            swallowUpKey = -1;
            return true;
        }
        if (grabbed >= 0 && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                moveGrabbed(code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_BACK) {
                if (event.getRepeatCount() == 0) {
                    grabbed = -1;
                    swallowUpKey = code;
                    refreshRows();
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** The result of the last 4K search, one line per source. */
    private void showDiag() {
        if (!isOn("wallpaper_diag")) {
            Toast.makeText(this, R.string.diag_off, Toast.LENGTH_LONG).show();
            return;
        }
        List<String[]> rows = WallpaperSources.parseDiag(choices.getString(SourcesWallpaper.DIAG_KEY, null));
        StringBuilder text = new StringBuilder();
        if (rows.isEmpty()) {
            text.append(getString(R.string.diag_none));
        } else {
            text.append(choices.getString(SourcesWallpaper.DIAG_DATE_KEY, ""));
            for (String[] r : rows) {
                text.append('\n').append(WallpaperSources.name(r[0])).append(": ")
                        .append(getString("1".equals(r[1]) ? R.string.diag_ok : R.string.diag_err));
                if (!r[2].isEmpty()) text.append(" - ").append(r[2]);
            }
        }
        new AlertDialog.Builder(this).setTitle(R.string.set_diag_view).setMessage(text)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void action(String key) {
        if (SettingsCatalog.DIAG_RESULTS.equals(key)) {
            showDiag();
        } else if (SettingsCatalog.FOLLOW_THEME.equals(key)) {
            if (!hasOnTvChoices()) return;
            Config.clearOnTvChoices(choices.edit()).commit();
            WallpaperUrls.deleteCache(this);
            SourcesWallpaper.deleteCache(this);
            changed(true);
        } else if (SettingsCatalog.HOME_PICKER.equals(key)) {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            start(new Intent(Settings.ACTION_HOME_SETTINGS));
        } else if (SettingsCatalog.TV_SETTINGS.equals(key)) {
            start(tvSettingsIntent());
        } else if (SettingsCatalog.SCREENSAVER.equals(key)) {
            start(new Intent(Settings.ACTION_DREAM_SETTINGS));
        } else if (SystemActions.handles(key)) {
            SystemActions.run(this, key, () -> changed(false));
        }
    }

    private interface Fetch {
        String run(android.content.Context ctx);
    }

    /** Bing or URL pictures: download off the main thread, then show the new picture here and on Home. */
    private void download(Fetch fetch) {
        work.execute(() -> {
            String err = fetch.run(this);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                Toast.makeText(this, err != null ? getString(R.string.wallpaper_url_failed, err)
                        : getString(R.string.wallpaper_applied), err != null ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
                changed(true);
            });
        });
    }

    private void showUrlEditor() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_VARIATION_URI);
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setText(WallpaperUrls.saved(this));
        input.setHint(R.string.wallpaper_url_hint);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        int pad = (int) (16 * dp);
        input.setPadding(pad, pad, pad, pad);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(pad, pad / 2, pad, 0);
        TextView hint = text(14, TEXT_2);
        hint.setText(R.string.wallpaper_url_hint);
        wrap.addView(hint);
        wrap.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_url_title)
                .setView(wrap)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> applyUrls(input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        input.requestFocus();
    }

    private void applyUrls(String text) {
        if (WallpaperUrls.parse(text).isEmpty()) {
            Toast.makeText(this, getString(R.string.wallpaper_url_failed, "empty"), Toast.LENGTH_LONG).show();
            return;
        }
        WallpaperUrls.save(this, text);
        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, WallpaperUrls.CHOICE).apply();
        changed(true);
        download(WallpaperUrls::downloadAll);
        if (screen().type == SettingsCatalog.CHOICE) back();
    }

    private boolean hasOnTvChoices() {
        for (String key : Config.FEATURES.keySet()) if (choices.contains(key)) return true;
        return choices.contains(SettingsCatalog.COLUMNS) || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(WallpaperUrls.URLS_KEY)
                || choices.contains(Config.WALLPAPER_ROTATION_KEY) || choices.contains(Config.WALLPAPER_TRANSITION_KEY)
                || choices.contains(Config.DOCK_GLASS_OPACITY_KEY) || choices.contains(WallpaperSources.ORDER_KEY)
                || choices.contains(WallpaperSources.STATE_KEY);
    }

    /** Same order as the Settings button on Home: Fire TV, Android TV settings, then ACTION_SETTINGS. */
    private Intent tvSettingsIntent() {
        if (getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setPackage("com.amazon.tv.launcher"), 0) != null) {
            Intent fire = new Intent().setComponent(new ComponentName(
                    "com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.MainSettingsActivity"));
            if (fire.resolveActivity(getPackageManager()) != null) return fire;
        }
        Intent tv = new Intent().setComponent(new ComponentName(
                "com.android.tv.settings", "com.android.tv.settings.MainSettings"));
        if (tv.resolveActivity(getPackageManager()) != null) return tv;
        return new Intent(Settings.ACTION_SETTINGS);
    }

    private void start(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }

    // ---- preview ---------------------------------------------------------------------------------

    /** The left column follows focus: the row's icon or picture, and its description, crossfaded. */
    private void preview(Item it) {
        SettingRow row = it.row;
        Object art = icon(row);
        File wallpaper = config.wallpaperEnabled ? config.wallpaper : null;
        if (it.label != null && SettingsCatalog.WALLPAPER.equals(row.key)) {
            File f = it.value instanceof String ? wallpaperFiles.get(it.value) : null;
            if (f != null) art = f;
            else if (it.value == null && wallpaper != null) art = wallpaper;
        } else if (wallpaper != null && (Integer.valueOf(R.drawable.ic_picture).equals(art)
                && (row.type == SettingsCatalog.SUBSCREEN || row.type == SettingsCatalog.CHOICE))) {
            art = wallpaper;
        }
        showArt(art);
        String text = it.source != null ? sourceDescription(it.source) : getString(row.description);
        if (!text.contentEquals(description.getText())) {
            fade(description, () -> description.setText(text));
        }
        soon.setVisibility(row.soon ? View.VISIBLE : View.INVISIBLE);
    }

    private String sourceDescription(String id) {
        if (WallpaperSources.unavailable(id, keys)) return getString(R.string.set_src_key_d, WallpaperSources.keyName(id));
        return getString(R.string.set_src_item_d);
    }

    /** The row's own icon, else the icon of the nearest screen above it. */
    private Integer icon(SettingRow row) {
        if (row.icon != 0) return row.icon;
        for (int i = path.size() - 1; i >= 0; i--) if (path.get(i).icon != 0) return path.get(i).icon;
        return R.drawable.ic_settings;
    }

    private void showArt(Object art) {
        if (art.equals(artShown)) return;
        artShown = art;
        if (art instanceof File) {
            Bitmap cached = thumbs.get(art);
            if (cached == null) {
                File f = (File) art;
                work.execute(() -> {
                    Bitmap b = decode(f, 640);
                    runOnUiThread(() -> {
                        if (b == null || isFinishing()) return;
                        thumbs.put(f, b);
                        if (f.equals(artShown)) {
                            artShown = null;
                            showArt(f);
                        }
                    });
                });
                return;
            }
        }
        ImageView next = artBack;
        artBack = artFront;
        artFront = next;
        if (art instanceof File) {
            next.setPadding(0, 0, 0, 0);
            next.setScaleType(ImageView.ScaleType.CENTER_CROP);
            next.setImageTintList(null);
            next.setImageBitmap(thumbs.get(art));
        } else {
            int pad = (int) (22 * dp);
            next.setPadding(pad, pad, pad, pad);
            next.setScaleType(ImageView.ScaleType.FIT_CENTER);
            next.setImageResource((Integer) art);
            next.setImageTintList(ColorStateList.valueOf(TEXT));
        }
        next.bringToFront();
        if (!motion) {
            next.setAlpha(1f);
            artBack.setAlpha(0f);
            return;
        }
        next.animate().cancel();
        next.setAlpha(0f);
        next.animate().alpha(1f).setDuration(200).start();
        artBack.animate().alpha(0f).setDuration(200).start();
    }

    private void fade(View v, Runnable change) {
        if (!motion) {
            change.run();
            return;
        }
        v.animate().cancel();
        v.animate().alpha(0f).setDuration(100).withEndAction(() -> {
            change.run();
            v.animate().alpha(1f).setDuration(100).start();
        }).start();
    }

    // ---- backdrop --------------------------------------------------------------------------------

    /** The current wallpaper behind everything, blurred: a 96 px wide copy blurred and scaled back up. */
    private void loadBackdrop() {
        File f = config.wallpaperEnabled ? config.wallpaper : null;
        if (f == null) {
            backdrop.setImageDrawable(null);
            return;
        }
        work.execute(() -> {
            Bitmap small = decode(f, 96);
            if (small == null) return;
            Bitmap blurred = blur(Bitmap.createScaledBitmap(small, 96, Math.max(1, 96 * small.getHeight() / small.getWidth()), true));
            runOnUiThread(() -> {
                if (isFinishing()) return;
                backdrop.setImageBitmap(blurred);
                if (motion) {
                    backdrop.setImageAlpha(0);
                    ValueAnimator a = ValueAnimator.ofInt(0, 255).setDuration(400);
                    a.addUpdateListener(an -> backdrop.setImageAlpha((Integer) an.getAnimatedValue()));
                    a.start();
                }
            });
        });
    }

    /** At least {@code width} px wide, decoded at a power-of-two scale; null if it does not decode. */
    private static Bitmap decode(File f, int width) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= width) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    private static Bitmap blur(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        int[] tmp = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 2; pass++) {
            boxBlur(px, tmp, w, h, 4);
            boxBlur(tmp, px, h, w, 4);
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    /** One horizontal box blur pass, written transposed so a second call blurs the other axis. */
    private static void boxBlur(int[] in, int[] out, int w, int h, int r) {
        int n = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w, red = 0, green = 0, blue = 0;
            for (int i = -r; i <= r; i++) {
                int c = in[row + Math.max(0, Math.min(i, w - 1))];
                red += (c >> 16) & 255;
                green += (c >> 8) & 255;
                blue += c & 255;
            }
            for (int x = 0; x < w; x++) {
                out[x * h + y] = 0xFF000000 | (red / n) << 16 | (green / n) << 8 | (blue / n);
                int add = in[row + Math.min(x + r + 1, w - 1)], drop = in[row + Math.max(x - r, 0)];
                red += ((add >> 16) & 255) - ((drop >> 16) & 255);
                green += ((add >> 8) & 255) - ((drop >> 8) & 255);
                blue += (add & 255) - (drop & 255);
            }
        }
    }

    // ---- rows ------------------------------------------------------------------------------------

    private final class Rows extends RecyclerView.Adapter<Rows.Holder> {
        final class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView value;
            final TextView chevron;
            final SpringAnimation scaleX;
            final SpringAnimation scaleY;

            Holder(LinearLayout row) {
                super(row);
                name = text(20, TEXT);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                value = text(19, TEXT_2);
                value.setSingleLine(true);
                value.setPadding((int) (16 * dp), 0, 0, 0);
                row.addView(value, wrap());
                chevron = text(26, TEXT_2);
                chevron.setText("›");
                chevron.setPadding((int) (12 * dp), 0, 0, (int) (3 * dp));
                row.addView(chevron, wrap());
                scaleX = spring(row, DynamicAnimation.SCALE_X);
                scaleY = spring(row, DynamicAnimation.SCALE_Y);
                row.setOnClickListener(v -> click(getBindingAdapterPosition()));
                row.setOnLongClickListener(v -> grab(getBindingAdapterPosition()));
                row.setOnFocusChangeListener((v, focused) -> {
                    float scale = focused ? 1.03f : 1f;
                    int p = getBindingAdapterPosition();
                    boolean option = p >= 0 && p < items.size() && items.get(p).label != null;
                    if (!option) value.setTextColor(focused ? 0xFFE5E5EA : TEXT_2);
                    if (motion) {
                        scaleX.animateToFinalPosition(scale);
                        scaleY.animateToFinalPosition(scale);
                        v.animate().translationZ(focused ? 4 * dp : 0f).setDuration(150).start();
                    } else {
                        v.setScaleX(scale);
                        v.setScaleY(scale);
                        v.setTranslationZ(focused ? 4 * dp : 0f);
                    }
                    if (focused && p >= 0 && p < items.size()) preview(items.get(p));
                });
            }

            /** OK on a switch: the value fades to its new text in 150 ms. */
            void value(String text, boolean animate) {
                if (text.contentEquals(value.getText())) return;
                if (animate && motion) {
                    value.animate().cancel();
                    value.animate().alpha(0f).setDuration(75).withEndAction(() -> {
                        value.setText(text);
                        value.animate().alpha(1f).setDuration(75).start();
                    }).start();
                } else {
                    value.setText(text);
                    value.setAlpha(1f);
                }
            }
        }

        private SpringAnimation spring(View v, DynamicAnimation.ViewProperty property) {
            SpringAnimation s = new SpringAnimation(v, property, 1f);
            s.getSpring().setStiffness(SpringForce.STIFFNESS_MEDIUM).setDampingRatio(0.72f);
            return s;
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setPadding((int) (20 * dp), 0, (int) (20 * dp), 0);
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_focused}, round(0x33FFFFFF, 12 * dp));
            bg.addState(new int[0], new ColorDrawable(0));
            if (motion) {
                bg.setEnterFadeDuration(150);
                bg.setExitFadeDuration(150);
            }
            row.setBackground(bg);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (54 * dp));
            // Side margins leave room inside the list for the focused row's 3% growth.
            lp.setMargins((int) (12 * dp), 0, (int) (12 * dp), (int) (4 * dp));
            row.setLayoutParams(lp);
            return new Holder(row);
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            Item it = items.get(position);
            h.name.setText(it.source != null ? WallpaperSources.name(it.source)
                    : it.label != null ? it.label : getString(it.row.title));
            h.value(valueOf(it), false);
            h.value.setTextSize(TypedValue.COMPLEX_UNIT_SP, it.label != null ? 22 : 19);
            // An option's tick is white, a value grey.
            h.value.setTextColor(it.label != null ? TEXT : h.itemView.isFocused() ? 0xFFE5E5EA : TEXT_2);
            boolean opens = it.label == null && it.source == null
                    && (it.row.type == SettingsCatalog.SOURCES || it.row.type == SettingsCatalog.SUBSCREEN || it.row.type == SettingsCatalog.CHOICE);
            h.chevron.setVisibility(opens ? View.VISIBLE : View.GONE);
            h.itemView.setAlpha(dim(it) ? 0.45f : 1f);
            boolean focused = h.itemView.isFocused();
            h.itemView.setScaleX(focused ? 1.03f : 1f);
            h.itemView.setScaleY(focused ? 1.03f : 1f);
            if (position == pendingFocus) {
                pendingFocus = -1;
                // A rebound view that already had focus gets no focus change, so the preview is set here too.
                h.itemView.post(() -> {
                    h.itemView.requestFocus();
                    if (position < items.size()) preview(items.get(position));
                });
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
