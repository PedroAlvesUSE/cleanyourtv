package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gathin.tvhome.wallpaper.DailyCollector;
import com.gathin.tvhome.wallpaper.Seasonal;
import com.gathin.tvhome.wallpaper.WallpaperAggregator;
import com.gathin.tvhome.wallpaper.WallpaperItem;
import com.gathin.tvhome.wallpaper.WallpaperProviders;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * "4K sources" wallpaper (wallpaper_choice = sources): up to six pictures of the day, from different sources, in an
 * on-disk cache like the URL and Bing ones. Each picture keeps its title and credit in {@code meta.tsv} beside the
 * files. Everything here blocks on the network: it only runs on the executor below or on the Settings executor.
 */
final class SourcesWallpaper {
    static final String CHOICE = "sources";
    static final String FEATURE = "wallpaper_sources";
    static final String DATE_KEY = "wallpaper_sources_date";
    static final String DIAG_KEY = "wallpaper_diag_result";
    static final String DIAG_DATE_KEY = "wallpaper_diag_date";
    static final int MAX_IMAGES = 6;
    private static final String TAG = "TvHomeWallpaper";
    private static final String DIR = "wallpaper_sources";
    private static final String META = "meta.tsv";
    private static final String TRY_KEY = "wallpaper_sources_try";
    /** Do not search again for this long after an attempt, so a broken network is not polled at every resume. */
    private static final long RETRY_MS = 30L * 60 * 1000;

    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wallpaper-sources");
        t.setDaemon(true);
        return t;
    });

    private SourcesWallpaper() {
    }

    // ---- cache -------------------------------------------------------------------------------------

    static File dir(Context ctx) {
        return new File(ctx.getFilesDir(), DIR);
    }

    static boolean isCached(File file) {
        File parent = file == null ? null : file.getParentFile();
        return parent != null && DIR.equals(parent.getName());
    }

    /** The cached pictures in the order they were collected; missing files are skipped. */
    static List<File> cachedFiles(Context ctx) {
        File dir = dir(ctx);
        List<File> out = new ArrayList<>();
        for (String[] row : readMeta(dir)) {
            File f = new File(dir, row[0]);
            if (f.isFile() && f.length() > 0) out.add(f);
        }
        return out;
    }

    static void deleteCache(Context ctx) {
        File[] files = dir(ctx).listFiles();
        if (files != null) for (File f : files) {
            // noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit().remove(DATE_KEY).apply();
    }

    /** {name, title, credit, provider} of each picture, in order. */
    static List<String[]> readMeta(File dir) {
        try {
            File f = new File(dir, META);
            if (!f.isFile()) return new ArrayList<>();
            return parseMeta(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return new ArrayList<>();
        }
    }

    /** title and credit of each cached file by file name, for the credit overlay. */
    static Map<String, String[]> credits(Context ctx) {
        Map<String, String[]> out = new LinkedHashMap<>();
        for (String[] row : readMeta(dir(ctx))) out.put(row[0], new String[]{row[1], row[2]});
        return out;
    }

    static String formatMeta(List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        for (String[] r : rows) {
            sb.append(WallpaperSources.clean(r[0])).append('\t').append(WallpaperSources.clean(r[1])).append('\t')
                    .append(WallpaperSources.clean(r[2])).append('\t').append(WallpaperSources.clean(r[3])).append('\n');
        }
        return sb.toString();
    }

    static List<String[]> parseMeta(String text) {
        List<String[]> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            String[] p = line.split("\t", -1);
            if (p.length >= 3 && !p[0].isEmpty()) {
                out.add(new String[]{p[0], p[1], p[2], p.length > 3 ? p[3] : ""});
            }
        }
        return out;
    }

    // ---- download ----------------------------------------------------------------------------------

    /** The file a picture is saved to: named after its main URL, so the same picture is not downloaded twice. */
    static File fileFor(File dir, WallpaperItem item) {
        return WallpaperUrls.fileIn(dir, item.urlUhd.isEmpty() ? item.urlHd : item.urlUhd);
    }

    /**
     * Saves the picture: the 4K URL first, the HD one when that fails or is over the size limit. The headers the
     * source asks for go with both requests. Returns null on success, else the last error.
     */
    static String downloadItem(WallpaperItem item, File dest) {
        if (dest.isFile() && dest.length() > 0) return null;
        List<String> urls = new ArrayList<>();
        if (WallpaperUrls.valid(item.urlUhd)) urls.add(item.urlUhd);
        if (WallpaperUrls.valid(item.urlHd) && !urls.contains(item.urlHd)) urls.add(item.urlHd);
        String err = "no url";
        for (String url : urls) {
            err = WallpaperUrls.downloadOne(url, dest, item.imageHeaders);
            if (err == null) return null;
            // noinspection ResultOfMethodCallIgnored
            dest.delete();
        }
        return err;
    }

    static WallpaperSources.Keys keys(Context ctx) {
        return new WallpaperSources.Keys(Config.apiKey(ctx, WallpaperSources.NASA_KEY),
                Config.apiKey(ctx, WallpaperSources.UNSPLASH_KEY), Config.apiKey(ctx, WallpaperSources.PEXELS_KEY));
    }

    /** Searches and downloads now. Null on success, else a short error. Call it off the main thread. */
    static String fetchAndCache(Context ctx) {
        if (!RUNNING.compareAndSet(false, true)) return "busy";
        try {
            return run(ctx.getApplicationContext());
        } finally {
            RUNNING.set(false);
        }
    }

    private static String run(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(TRY_KEY, System.currentTimeMillis()).apply();
        WallpaperSources.Keys keys = keys(ctx);
        List<String> order = WallpaperSources.active(prefs.getString(WallpaperSources.ORDER_KEY, null),
                prefs.getString(WallpaperSources.STATE_KEY, null), keys);
        if (order.isEmpty()) return "no sources";
        LocalDate today = LocalDate.now();
        String query = Config.feature(ctx, "seasonal_wallpaper") ? Seasonal.query(today) : null;
        if (query != null) {
            order = WallpaperSources.seasonal(order, query);
            Log.i(TAG, "seasonal term: " + query);
        }
        File dir = dir(ctx);
        // noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        WallpaperAggregator aggregator = WallpaperProviders.newAggregator(new File(ctx.getFilesDir(), "wallpaper_catalog"),
                keys.nasa, keys.unsplash, keys.pexels, query);
        DailyCollector.Collected got = DailyCollector.collect(aggregator, order, today, MAX_IMAGES,
                item -> downloadItem(item, fileFor(dir, item)));
        for (DailyCollector.Outcome o : got.outcomes) {
            Log.i(TAG, o.provider + (o.ok ? " ok " : " error ") + o.message);
        }
        if (Config.feature(ctx, "wallpaper_diag")) saveDiag(prefs, got.outcomes, today);
        if (got.items.isEmpty()) {
            String first = got.outcomes.isEmpty() ? "no sources" : got.outcomes.get(0).provider + ": "
                    + got.outcomes.get(0).message;
            return first;
        }
        List<String[]> meta = new ArrayList<>();
        Set<String> keep = new HashSet<>();
        keep.add(META);
        for (WallpaperItem item : got.items) {
            File f = fileFor(dir, item);
            keep.add(f.getName());
            meta.add(new String[]{f.getName(), item.title, item.copyright, item.provider});
        }
        try (FileOutputStream out = new FileOutputStream(new File(dir, META))) {
            out.write(formatMeta(meta).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.w(TAG, "meta not saved", e);
        }
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) {
            if (!keep.contains(f.getName())) {
                // noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
        WallpaperUrls.pruneToBudget(dir, WallpaperUrls.MAX_CACHE_BYTES);
        prefs.edit().putString(DATE_KEY, today.toString()).apply();
        return null;
    }

    private static void saveDiag(SharedPreferences prefs, List<DailyCollector.Outcome> outcomes, LocalDate day) {
        List<String[]> rows = new ArrayList<>();
        for (DailyCollector.Outcome o : outcomes) rows.add(new String[]{o.provider, o.ok ? "1" : "0", o.message});
        prefs.edit().putString(DIAG_KEY, WallpaperSources.formatDiag(rows)).putString(DIAG_DATE_KEY, day.toString())
                .apply();
    }

    // ---- daily refresh -----------------------------------------------------------------------------

    /** The search date saved by the last successful search, or "". */
    static boolean dueToday(SharedPreferences prefs, LocalDate today, long nowMs) {
        if (!CHOICE.equals(prefs.getString(Config.WALLPAPER_CHOICE_KEY, null))) return false;
        if (today.toString().equals(prefs.getString(DATE_KEY, ""))) return false;
        return nowMs - prefs.getLong(TRY_KEY, 0) >= RETRY_MS;
    }

    static boolean online(Context ctx) {
        ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /**
     * Home start: when the sources choice is on and the day changed since the last search, searches in the
     * background (only with a network) and then runs {@code done} on the main thread. Nothing runs when the choice
     * or the function is off. No alarm: the check happens when Home starts.
     */
    static void refreshIfDue(Context ctx, Runnable done) {
        Context app = ctx.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        if (!dueToday(prefs, LocalDate.now(), System.currentTimeMillis())) return;
        if (!Config.feature(app, FEATURE) || !online(app)) return;
        EXECUTOR.execute(() -> {
            String err = fetchAndCache(app);
            if (err == null) new Handler(Looper.getMainLooper()).post(done);
            else Log.w(TAG, "daily search failed: " + err);
        });
    }
}
