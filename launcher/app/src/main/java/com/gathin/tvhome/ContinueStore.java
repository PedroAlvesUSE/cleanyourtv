package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Continue watching (docs/specs/continue.md): the items, their checks and the rules that keep the list small.
 * Pure Java plus org.json, so the JVM tests run it as is. The provider, the media session source and the Home
 * row only call into here; none of them keeps its own copy of the rules.
 */
final class ContinueStore {
    static final String FEATURE = "continue_watching";
    static final String FEATURE_MEDIA = "continue_media_session";

    static final String SOURCE_PROVIDER = "provider";
    static final String SOURCE_MEDIA = "media";

    /** Items kept per app; the oldest go first. */
    static final int MAX_PER_APP = 20;
    /** Items kept in all; the oldest go first. */
    static final int MAX_TOTAL = 200;
    /** An item not updated for this long is dropped. */
    static final long EXPIRY_MS = 30L * 24 * 60 * 60 * 1000;
    /** Shown on the Home row, newest first. */
    static final int MAX_SHOWN = 12;
    /** An item at or past this progress is finished: kept, but not shown. */
    static final float FINISHED = 0.98f;

    static final int ID_MAX = 128;
    static final int TITLE_MAX = 120;
    static final int SUBTITLE_MAX = 120;
    static final int URL_MAX = 2048;

    static final class Item {
        final String pkg;
        final String id;
        final String title;
        /** Null when there is none. */
        final String subtitle;
        /** http or https; null when there is none. */
        final String imageUrl;
        /** Absolute path of a picture the launcher saved (media session art); null when there is none. */
        final String imageFile;
        /** Null: the card opens the app. */
        final String deepLink;
        /** 0..1. */
        final float progress;
        /** 0 when not known. */
        final long durationMs;
        final long updatedAt;
        final String source;

        Item(String pkg, String id, String title, String subtitle, String imageUrl, String imageFile, String deepLink,
             float progress, long durationMs, long updatedAt, String source) {
            this.pkg = pkg;
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.imageUrl = imageUrl;
            this.imageFile = imageFile;
            this.deepLink = deepLink;
            this.progress = progress;
            this.durationMs = durationMs;
            this.updatedAt = updatedAt;
            this.source = source;
        }

        String key() {
            return ContinueStore.key(pkg, id);
        }
    }

    private ContinueStore() {
    }

    static String key(String pkg, String id) {
        return pkg + '\n' + id;
    }

    // ---- checks ----------------------------------------------------------------------------------

    /** Trimmed text without control characters, cut to max; null when nothing is left. */
    static String cleanText(String s, int max) {
        if (s == null) return null;
        StringBuilder b = new StringBuilder(Math.min(s.length(), max));
        for (int i = 0; i < s.length() && b.length() < max; i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\t' || c == '\r') c = ' ';
            if (Character.isISOControl(c)) continue;
            b.append(c);
        }
        String out = b.toString().trim();
        return out.isEmpty() ? null : out;
    }

    /** An image address: http or https, a host, no spaces, at most URL_MAX characters. Null when it fails. */
    static String imageUrl(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty() || s.length() > URL_MAX) return null;
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        int start;
        if (lower.startsWith("https://")) start = 8;
        else if (lower.startsWith("http://")) start = 7;
        else return null;
        if (start >= s.length() || s.charAt(start) == '/') return null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= ' ' || c >= 0x7F) return null;
        }
        return s;
    }

    /**
     * A deep link: any scheme (letters, digits, + - .) with something after the colon, no spaces, at most URL_MAX
     * characters. file: is refused. The launcher always opens it inside the item's own package.
     */
    static String deepLink(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty() || s.length() > URL_MAX) return null;
        int colon = s.indexOf(':');
        if (colon <= 0 || colon == s.length() - 1) return null;
        for (int i = 0; i < colon; i++) {
            char c = s.charAt(i);
            boolean letter = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
            if (!(letter || i > 0 && (c >= '0' && c <= '9' || c == '+' || c == '-' || c == '.'))) return null;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= ' ' || c == 0x7F) return null;
        }
        if (s.substring(0, colon).equalsIgnoreCase("file")) return null;
        return s;
    }

    /** An item id: 1 to ID_MAX printable ASCII characters. */
    static String itemId(String s) {
        if (s == null || s.isEmpty() || s.length() > ID_MAX) return null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= ' ' || c >= 0x7F) return null;
        }
        return s;
    }

    /** NaN and anything out of range land inside 0..1. */
    static float progress(double p) {
        if (Double.isNaN(p) || p < 0) return 0f;
        return p > 1 ? 1f : (float) p;
    }

    /**
     * An item from what an app wrote, or null with a reason in {@code why[0]}. The time is the launcher's own:
     * an app cannot date its item in the future to stay first.
     */
    static Item fromValues(String pkg, String id, String title, String subtitle, String image, String link,
                           Double progress, Long durationMs, long now, String[] why) {
        String cleanId = itemId(id);
        if (cleanId == null) return reject(why, "bad item_id");
        String cleanTitle = cleanText(title, TITLE_MAX);
        if (cleanTitle == null) return reject(why, "missing title");
        String cleanImage = imageUrl(image);
        if (image != null && !image.trim().isEmpty() && cleanImage == null) return reject(why, "bad image_url");
        String cleanLink = deepLink(link);
        if (link != null && !link.trim().isEmpty() && cleanLink == null) return reject(why, "bad deep_link");
        long duration = durationMs == null || durationMs < 0 ? 0 : durationMs;
        return new Item(pkg, cleanId, cleanTitle, cleanText(subtitle, SUBTITLE_MAX), cleanImage, null, cleanLink,
                progress(progress == null ? 0 : progress), duration, now, SOURCE_PROVIDER);
    }

    /** A media session shorter than this (music, a clip, an ad) is not something to continue. */
    static final long MEDIA_MIN_DURATION_MS = 2 * 60 * 1000;

    /**
     * An item from a media session's metadata and playback position, or null when there is nothing to continue.
     * Title: display title, else title. Subtitle: display subtitle, else artist, else album. Picture: an http(s)
     * art address, else the saved art file. The id is derived from the title, so each episode is its own item.
     */
    static Item fromMedia(String pkg, String displayTitle, String title, String displaySubtitle, String artist,
                          String album, String artUri, String artFile, long positionMs, long durationMs, long now) {
        String t = cleanText(displayTitle, TITLE_MAX);
        if (t == null) t = cleanText(title, TITLE_MAX);
        if (t == null) return null;
        if (durationMs > 0 && durationMs < MEDIA_MIN_DURATION_MS) return null;
        String sub = cleanText(displaySubtitle, SUBTITLE_MAX);
        if (sub == null) sub = cleanText(artist, SUBTITLE_MAX);
        if (sub == null) sub = cleanText(album, SUBTITLE_MAX);
        if (sub != null && sub.equalsIgnoreCase(t)) sub = null;
        long duration = Math.max(0, durationMs);
        float p = duration > 0 ? progress((double) Math.max(0, positionMs) / duration) : 0f;
        String url = imageUrl(artUri);
        String id = "ms:" + Integer.toHexString(t.toLowerCase(java.util.Locale.ROOT).hashCode());
        return new Item(pkg, id, t, sub, url, url == null ? artFile : null, null, p, duration, now, SOURCE_MEDIA);
    }

    private static Item reject(String[] why, String reason) {
        if (why != null && why.length > 0) why[0] = reason;
        return null;
    }

    // ---- list rules ------------------------------------------------------------------------------

    /**
     * Adds or replaces the item with the same (package, item_id), then applies the limits. An app that writes
     * through the provider wins over the media session: its media items go, and a media item for an app with
     * provider items is not added. Returns false when the item was not added.
     */
    static boolean upsert(List<Item> items, Item item, long now) {
        boolean fromProvider = SOURCE_PROVIDER.equals(item.source);
        if (!fromProvider) {
            for (Item it : items) {
                if (it.pkg.equals(item.pkg) && SOURCE_PROVIDER.equals(it.source) && !expired(it, now)) return false;
            }
        }
        String key = item.key();
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            if (it.key().equals(key) || fromProvider && it.pkg.equals(item.pkg) && SOURCE_MEDIA.equals(it.source)) {
                items.remove(i);
            }
        }
        items.add(item);
        prune(items, now);
        return items.contains(item);
    }

    /** Removes one item, or every item of the package when id is null. Returns how many went. */
    static int remove(List<Item> items, String pkg, String id) {
        int n = 0;
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            if (it.pkg.equals(pkg) && (id == null || it.id.equals(id))) {
                items.remove(i);
                n++;
            }
        }
        return n;
    }

    static boolean expired(Item it, long now) {
        return now - it.updatedAt > EXPIRY_MS;
    }

    /** Drops expired items, then the oldest past MAX_PER_APP for each app and past MAX_TOTAL in all. */
    static void prune(List<Item> items, long now) {
        for (int i = items.size() - 1; i >= 0; i--) if (expired(items.get(i), now)) items.remove(i);
        sortNewest(items);
        Map<String, Integer> perApp = new HashMap<>();
        List<Item> kept = new ArrayList<>(items.size());
        for (Item it : items) {
            int n = perApp.containsKey(it.pkg) ? perApp.get(it.pkg) : 0;
            if (n >= MAX_PER_APP || kept.size() >= MAX_TOTAL) continue;
            perApp.put(it.pkg, n + 1);
            kept.add(it);
        }
        items.clear();
        items.addAll(kept);
    }

    /** What the Home row shows: not expired, not finished, from an allowed app, newest first. */
    static List<Item> visible(List<Item> items, java.util.function.Predicate<Item> allowed, long now) {
        List<Item> out = new ArrayList<>();
        for (Item it : items) {
            if (expired(it, now) || it.progress >= FINISHED || !allowed.test(it)) continue;
            out.add(it);
        }
        sortNewest(out);
        return out.size() > MAX_SHOWN ? new ArrayList<>(out.subList(0, MAX_SHOWN)) : out;
    }

    static void sortNewest(List<Item> items) {
        Collections.sort(items, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
    }

    // ---- file ------------------------------------------------------------------------------------

    static String toJson(List<Item> items) {
        JSONArray arr = new JSONArray();
        try {
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("pkg", it.pkg);
                o.put("id", it.id);
                o.put("title", it.title);
                if (it.subtitle != null) o.put("subtitle", it.subtitle);
                if (it.imageUrl != null) o.put("image_url", it.imageUrl);
                if (it.imageFile != null) o.put("image_file", it.imageFile);
                if (it.deepLink != null) o.put("deep_link", it.deepLink);
                o.put("progress", (double) it.progress);
                if (it.durationMs > 0) o.put("duration_ms", it.durationMs);
                o.put("updated_at", it.updatedAt);
                o.put("source", it.source);
                arr.put(o);
            }
        } catch (Exception ignored) {
            // put() only throws for NaN, which progress() never returns.
        }
        return arr.toString();
    }

    /** The items in the file text; a bad entry is skipped and a bad file reads as empty. */
    static List<Item> fromJson(String text) {
        List<Item> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(text);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String pkg = o.optString("pkg", null), id = itemId(o.optString("id", null));
                String title = cleanText(o.optString("title", null), TITLE_MAX);
                if (pkg == null || id == null || title == null) continue;
                String source = SOURCE_MEDIA.equals(o.optString("source")) ? SOURCE_MEDIA : SOURCE_PROVIDER;
                out.add(new Item(pkg, id, title, cleanText(o.optString("subtitle", null), SUBTITLE_MAX),
                        imageUrl(o.optString("image_url", null)), o.optString("image_file", null),
                        deepLink(o.optString("deep_link", null)), progress(o.optDouble("progress", 0)),
                        Math.max(0, o.optLong("duration_ms", 0)), o.optLong("updated_at", 0), source));
            }
        } catch (Exception e) {
            out.clear();
        }
        return out;
    }
}
