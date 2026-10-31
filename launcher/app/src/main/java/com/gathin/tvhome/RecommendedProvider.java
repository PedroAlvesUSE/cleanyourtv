package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Recommended videos from YouTube, without an API key (recommended_row): the trending list of a public Piped or
 * Invidious instance, in the BR region. Instances come and go, so the request tries each in turn with a short
 * timeout and keeps the first answer for an hour. Pictures come straight from i.ytimg.com, which does not depend
 * on the instance that served the list.
 */
final class RecommendedProvider {
    private static final long CACHE_MS = 60L * 60 * 1000;
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 7000;

    /** Public Piped instances (no key), the ones that answered when this was written first. */
    private static final String[] PIPED = {
            "https://api.piped.private.coffee",
            "https://pipedapi.ducks.party",
            "https://pipedapi.adminforge.de",
    };
    /** Public Invidious instances (no key), tried when every Piped one fails. */
    private static final String[] INVIDIOUS = {
            "https://yewtu.be",
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
    };

    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    private static volatile List<Item> cache = new ArrayList<>();
    private static volatile long cachedAt;

    static final class Item {
        final String videoId;
        final String title;
        final String channel;
        final String thumb;   // https URL
        final int durationSec;
        Item(String videoId, String title, String channel, String thumb, int durationSec) {
            this.videoId = videoId; this.title = title; this.channel = channel;
            this.thumb = thumb; this.durationSec = durationSec;
        }
    }

    private RecommendedProvider() { }

    /** Fetches the list; the callback runs on the main thread with an empty list when every instance failed. */
    static void fetch(Context ctx, String region, Consumer<List<Item>> done) {
        long now = System.currentTimeMillis();
        if (!cache.isEmpty() && now - cachedAt < CACHE_MS) {
            List<Item> hit = cache;
            MAIN.post(() -> done.accept(hit));
            return;
        }
        WORK.execute(() -> {
            List<Item> out = null;
            for (String base : PIPED) {
                out = piped(base, region);
                if (out != null && !out.isEmpty()) break;
            }
            if (out == null || out.isEmpty()) {
                for (String base : INVIDIOUS) {
                    out = invidious(base, region);
                    if (out != null && !out.isEmpty()) break;
                }
            }
            if (out != null && !out.isEmpty()) {
                cache = out;
                cachedAt = now;
            }
            final List<Item> result = out == null ? new ArrayList<>() : out;
            MAIN.post(() -> done.accept(result));
        });
    }

    private static List<Item> piped(String base, String region) {
        try {
            JSONArray arr = new JSONArray(get(base + "/trending?region=" + region + "&category=videos&page=1"));
            List<Item> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if ("stream".equals(o.optString("type", "stream")) && o.optBoolean("isShort", false)) continue;
                String id = idOf(o.optString("videoId", null), o.optString("url", null));
                String title = o.optString("title", null);
                if (id == null || title == null) continue;
                int dur = o.optInt("durationSeconds", o.optInt("duration", 0));
                out.add(new Item(id, title, o.optString("uploaderName", null), thumb(id), dur));
            }
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<Item> invidious(String base, String region) {
        try {
            JSONArray arr = new JSONArray(get(base + "/api/v1/trending?region=" + region
                    + "&type=video&sortBy=viewCount"));
            List<Item> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.optString("videoId", null);
                String title = o.optString("title", null);
                if (id == null || id.isEmpty() || title == null) continue;
                out.add(new Item(id, title, o.optString("author", null), thumb(id),
                        o.optInt("lengthSeconds", 0)));
            }
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** The id in the "videoId" field, or the one after "v=" in a "/watch?v=..." URL. */
    private static String idOf(String videoId, String url) {
        if (videoId != null && !videoId.isEmpty()) return videoId;
        if (url == null) return null;
        int q = url.indexOf("v=");
        if (q < 0) return null;
        int end = url.indexOf('&', q);
        String id = end < 0 ? url.substring(q + 2) : url.substring(q + 2, end);
        return id.isEmpty() ? null : id;
    }

    /** A 16:9 thumbnail that does not depend on the instance: YouTube's own medium picture. */
    private static String thumb(String videoId) {
        return "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg";
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_TIMEOUT);
        c.setReadTimeout(READ_TIMEOUT);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "CleanYourTV/2.4");
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) return "";
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder b = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) b.append(buf, 0, n);
            return b.toString();
        } finally {
            c.disconnect();
        }
    }
}
