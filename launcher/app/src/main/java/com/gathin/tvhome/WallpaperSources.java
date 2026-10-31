package com.gathin.tvhome;

import com.gathin.tvhome.wallpaper.WallpaperProviders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The 4K sources the user ordered and switched on or off, kept as two strings in launcher_settings: the order
 * ({@code a,b,c}) and the explicit states ({@code a:1,b:0}). Pure Java, no Android, so the rules are unit tested.
 */
final class WallpaperSources {
    static final String ORDER_KEY = "wallpaper_sources_order";
    static final String STATE_KEY = "wallpaper_sources_state";
    static final String NASA_KEY = "nasa_key";
    static final String UNSPLASH_KEY = "unsplash_key";
    static final String PEXELS_KEY = "pexels_key";

    private WallpaperSources() {
    }

    /** The API keys of config.json; an empty one means "no key". */
    static final class Keys {
        final String nasa;
        final String unsplash;
        final String pexels;

        Keys(String nasa, String unsplash, String pexels) {
            this.nasa = nasa == null ? "" : nasa.trim();
            this.unsplash = unsplash == null ? "" : unsplash.trim();
            this.pexels = pexels == null ? "" : pexels.trim();
        }
    }

    /** Sources that give nothing without a key. */
    static boolean needsKey(String id) {
        return "unsplash".equals(id) || "pexels".equals(id);
    }

    /** The config.json key a source needs, or null. */
    static String keyName(String id) {
        return "unsplash".equals(id) ? UNSPLASH_KEY : "pexels".equals(id) ? PEXELS_KEY : null;
    }

    static boolean hasKey(String id, Keys keys) {
        if ("unsplash".equals(id)) return !keys.unsplash.isEmpty();
        if ("pexels".equals(id)) return !keys.pexels.isEmpty();
        return true;
    }

    /** A source that cannot be switched on: it needs a key and has none. */
    static boolean unavailable(String id, Keys keys) {
        return needsKey(id) && !hasKey(id, keys);
    }

    /** Stored order first, then the sources it misses (default order first). Unknown ids are dropped. */
    static List<String> order(String stored) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (stored != null) {
            for (String id : stored.split(",")) {
                id = id.trim();
                if (WallpaperProviders.ALL_IDS.contains(id)) out.add(id);
            }
        }
        out.addAll(WallpaperProviders.DEFAULT_ORDER);
        out.addAll(WallpaperProviders.ALL_IDS);
        return new ArrayList<>(out);
    }

    static String joinOrder(List<String> order) {
        StringBuilder sb = new StringBuilder();
        for (String id : order) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    static Map<String, Boolean> states(String stored) {
        Map<String, Boolean> out = new HashMap<>();
        if (stored == null) return out;
        for (String part : stored.split(",")) {
            int colon = part.indexOf(':');
            if (colon <= 0) continue;
            String id = part.substring(0, colon).trim();
            if (WallpaperProviders.ALL_IDS.contains(id)) out.put(id, "1".equals(part.substring(colon + 1).trim()));
        }
        return out;
    }

    /** The stored states with one source changed. */
    static String withState(String stored, String id, boolean on) {
        Map<String, Boolean> map = states(stored);
        map.put(id, on);
        StringBuilder sb = new StringBuilder();
        for (String known : WallpaperProviders.ALL_IDS) {
            Boolean v = map.get(known);
            if (v == null) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(known).append(':').append(v ? 1 : 0);
        }
        return sb.toString();
    }

    /** On: the explicit choice, else the default (the suggested order; key-only sources only with a key). */
    static boolean enabled(String id, String storedStates, Keys keys) {
        if (unavailable(id, keys)) return false;
        Boolean explicit = states(storedStates).get(id);
        if (explicit != null) return explicit;
        return needsKey(id) || WallpaperProviders.DEFAULT_ORDER.contains(id);
    }

    /** The sources to try, in the user's order. */
    static List<String> active(String storedOrder, String storedStates, Keys keys) {
        List<String> out = new ArrayList<>();
        for (String id : order(storedOrder)) if (enabled(id, storedStates, keys)) out.add(id);
        return out;
    }

    /** Wallhaven first when a holiday search term applies and the user left Wallhaven on. */
    static List<String> seasonal(List<String> active, String query) {
        List<String> out = new ArrayList<>(active);
        if (query != null && out.remove("wallhaven")) out.add(0, "wallhaven");
        return out;
    }

    /** Moves the entry at {@code from} by {@code delta}; false when it would leave the list. */
    static boolean move(List<String> order, int from, int delta) {
        int to = from + delta;
        if (from < 0 || from >= order.size() || to < 0 || to >= order.size()) return false;
        order.add(to, order.remove(from));
        return true;
    }

    /** The name a row shows. */
    static String name(String id) {
        switch (id) {
            case "bing": return "Bing";
            case "peapix_bing": return "Peapix Bing";
            case "biturl": return "BitUrl Bing";
            case "spotlight": return "Windows Spotlight";
            case "peapix_spotlight": return "Peapix Spotlight";
            case "chromecast": return "Chromecast";
            case "earthview": return "Google Earth View";
            case "wikimedia": return "Wikimedia";
            case "nasa": return "NASA APOD";
            case "wallhaven": return "Wallhaven";
            case "reddit_earthporn": return "Reddit EarthPorn";
            case "reddit_wallpapers": return "Reddit Wallpapers";
            case "reddit_amoledbackgrounds": return "Reddit AMOLED";
            case "artic": return "Art Institute of Chicago";
            case "met": return "The Met";
            case "picsum": return "Picsum";
            case "unsplash": return "Unsplash";
            case "pexels": return "Pexels";
            default: return id.toUpperCase(Locale.ROOT);
        }
    }

    // ---- last search result (diagnostics) ----------------------------------------------------------

    /** One line per source tried: {@code id TAB 1|0 TAB message}. Tabs and line breaks in the message are removed. */
    static String formatDiag(List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        for (String[] r : rows) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(clean(r[0])).append('\t').append(clean(r[1])).append('\t').append(clean(r[2]));
        }
        return sb.toString();
    }

    /** The rows of {@link #formatDiag}: {id, "1" or "0", message}. */
    static List<String[]> parseDiag(String stored) {
        List<String[]> out = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return out;
        for (String line : stored.split("\n")) {
            String[] p = line.split("\t", -1);
            if (p.length >= 2) out.add(new String[]{p[0], p[1], p.length > 2 ? p[2] : ""});
        }
        return out;
    }

    static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }
}
