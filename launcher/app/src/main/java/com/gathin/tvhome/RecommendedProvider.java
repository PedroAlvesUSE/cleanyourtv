package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Prateleira Recomendados da conta do YouTube (recommended_row): a mesma da Home do YouTube de TV
 * depois do login, via browse FEwhat_to_watch no cliente de TV. A sessão fica nas preferências
 * privadas do launcher. Na primeira vez, um arquivo de uso único em getExternalFilesDir pode
 * entregar o refresh token (é apagado em seguida); sem isso, a linha mostra um código para
 * vincular a mesma conta em google.com/device.
 */
final class RecommendedProvider {
    private static final long CACHE_MS = 30L * 60 * 1000;
    private static final int CONNECT_TIMEOUT = 8000;
    private static final int READ_TIMEOUT = 12000;
    private static final int MAX_ITEMS = 24;
    private static final String PREFS = "youtube_tv";
    private static final String BROWSE = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String DEVICE_URL = "https://oauth2.googleapis.com/device/code";
    /** Cliente público do YouTube de TV (emite o código de vinculação da conta). */
    private static final String CLIENT_ID =
            "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com";
    private static final String CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT";
    private static final String CLIENT_VERSION = "7.20250305.16.00";
    private static final Pattern DURATION = Pattern.compile("^\\d{1,2}:\\d{2}(?::\\d{2})?$");

    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static volatile android.os.Handler main;
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static volatile List<Item> cache = new ArrayList<>();
    private static volatile long cachedAt;
    private static volatile String accessToken;
    private static volatile long accessUntil;

    static final class Item {
        final String videoId;
        final String title;
        final String channel;
        final String thumb;
        final int durationSec;
        Item(String videoId, String title, String channel, String thumb, int durationSec) {
            this.videoId = videoId;
            this.title = title;
            this.channel = channel;
            this.thumb = thumb;
            this.durationSec = durationSec;
        }
    }

    /** Lista pronta, ou um código para vincular a conta quando ainda não há sessão. */
    static final class Result {
        final List<Item> items;
        final String linkCode;
        final String linkUrl;
        Result(List<Item> items, String linkCode, String linkUrl) {
            this.items = items;
            this.linkCode = linkCode;
            this.linkUrl = linkUrl;
        }
        static Result videos(List<Item> items) {
            return new Result(items, null, null);
        }
    }

    private RecommendedProvider() { }

    /** Cancela um pareamento em andamento (a linha saiu da tela). */
    static void cancel() {
        GENERATION.incrementAndGet();
    }

    /** Busca a prateleira; o callback roda na thread principal, mais de uma vez se houver código. */
    static void fetch(Context ctx, Consumer<Result> done) {
        long now = System.currentTimeMillis();
        if (!cache.isEmpty() && now - cachedAt < CACHE_MS) {
            List<Item> hit = cache;
            mainHandler().post(() -> done.accept(Result.videos(hit)));
            return;
        }
        Context app = ctx.getApplicationContext();
        int gen = GENERATION.incrementAndGet();
        WORK.execute(() -> run(app, gen, done));
    }

    private static void run(Context ctx, int gen, Consumer<Result> done) {
        String refresh = importRefresh(ctx);
        if (refresh == null) refresh = prefs(ctx).getString("refresh", null);
        if (refresh != null) {
            List<Item> items = home(refresh);
            if (items != null) {
                if (!items.isEmpty()) {
                    cache = items;
                    cachedAt = System.currentTimeMillis();
                }
                post(done, Result.videos(items), gen);
                return;
            }
            prefs(ctx).edit().remove("refresh").apply();
            accessToken = null;
        }
        if (!link(ctx, gen, done)) post(done, Result.videos(new ArrayList<>()), gen);
    }

    /** Arquivo de uma vez em getExternalFilesDir; some assim que a sessão entra nas preferências. */
    private static String importRefresh(Context ctx) {
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) return null;
        File file = new File(dir, "youtube-refresh");
        if (!file.isFile()) return null;
        String token = null;
        try {
            String text = read(new java.io.FileInputStream(file)).trim();
            if (text.startsWith("1//") && text.length() > 20 && !text.contains("\n") && !text.contains(" ")) {
                prefs(ctx).edit().putString("refresh", text).apply();
                token = text;
            }
        } catch (Exception ignored) {
            token = null;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        return token;
    }

    /** Troca o refresh token por um access token e lê a prateleira. null quando a sessão morreu. */
    private static List<Item> home(String refresh) {
        try {
            String access = access(refresh);
            if (access == null) return null;
            String json = postJson(BROWSE, browseBody(), access);
            if (json.contains("feedNudgeRenderer") && !json.contains("shelfRenderer")) return null;
            return recommendedShelf(json);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private static String access(String refresh) throws Exception {
        long now = System.currentTimeMillis();
        if (accessToken != null && now < accessUntil) return accessToken;
        String body = "client_id=" + enc(CLIENT_ID)
                + "&client_secret=" + enc(CLIENT_SECRET)
                + "&refresh_token=" + enc(refresh)
                + "&grant_type=refresh_token";
        JSONObject o = new JSONObject(postForm(TOKEN_URL, body));
        if (!o.has("access_token")) return null;
        accessToken = o.getString("access_token");
        accessUntil = now + Math.max(60, o.optInt("expires_in", 3600) - 120) * 1000L;
        return accessToken;
    }

    /** Código de aparelho. Devolve false se o pedido nem chegou a mostrar um código. */
    private static boolean link(Context ctx, int gen, Consumer<Result> done) {
        try {
            String body = "client_id=" + enc(CLIENT_ID)
                    + "&scope=" + enc("https://www.googleapis.com/auth/youtube");
            JSONObject start = new JSONObject(postForm(DEVICE_URL, body));
            String device = start.optString("device_code", "");
            String userCode = start.optString("user_code", "");
            String url = start.optString("verification_url", "https://www.google.com/device");
            if (device.isEmpty() || userCode.isEmpty()) return false;
            post(done, new Result(new ArrayList<>(), userCode, displayUrl(url)), gen);
            int interval = Math.max(5, start.optInt("interval", 5));
            long deadline = System.currentTimeMillis() + start.optInt("expires_in", 600) * 1000L;
            String poll = "client_id=" + enc(CLIENT_ID)
                    + "&client_secret=" + enc(CLIENT_SECRET)
                    + "&device_code=" + enc(device)
                    + "&grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code");
            while (System.currentTimeMillis() < deadline && gen == GENERATION.get()) {
                sleep(interval);
                if (gen != GENERATION.get()) return true;
                JSONObject o = new JSONObject(postForm(TOKEN_URL, poll));
                if (o.has("refresh_token")) {
                    prefs(ctx).edit().putString("refresh", o.getString("refresh_token")).apply();
                    accessToken = o.optString("access_token", null);
                    accessUntil = System.currentTimeMillis()
                            + Math.max(60, o.optInt("expires_in", 3600) - 120) * 1000L;
                    List<Item> items = home(o.getString("refresh_token"));
                    if (items == null) items = new ArrayList<>();
                    if (!items.isEmpty()) {
                        cache = items;
                        cachedAt = System.currentTimeMillis();
                    }
                    post(done, Result.videos(items), gen);
                    return true;
                }
                String err = o.optString("error", "");
                if ("slow_down".equals(err)) interval += 2;
                else if (!"authorization_pending".equals(err) && !err.isEmpty()) return true;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Vídeos da prateleira cujo título é Recomendados / Recommended. */
    static List<Item> recommendedShelf(String json) throws Exception {
        List<Item> out = new ArrayList<>();
        findShelf(new JSONObject(json), out);
        return out;
    }

    private static boolean findShelf(Object node, List<Item> out) throws Exception {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            if (o.has("shelfRenderer")) {
                JSONObject shelf = o.getJSONObject("shelfRenderer");
                if (isRecommended(shelf)) {
                    fill(shelf, out);
                    return true;
                }
            }
            JSONArray names = o.names();
            if (names == null) return false;
            for (int i = 0; i < names.length(); i++) {
                String name = names.getString(i);
                if ("shelfRenderer".equals(name)) continue;
                if (findShelf(o.opt(name), out)) return true;
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) {
                if (findShelf(a.opt(i), out)) return true;
            }
        }
        return false;
    }

    private static boolean isRecommended(JSONObject shelf) throws Exception {
        String title = String.join(" ", texts(shelf.optJSONObject("headerRenderer"))).toLowerCase(Locale.ROOT);
        return title.contains("recomend") || title.contains("recommend");
    }

    private static void fill(JSONObject shelf, List<Item> out) throws Exception {
        JSONObject content = shelf.optJSONObject("content");
        JSONObject list = content == null ? null : content.optJSONObject("horizontalListRenderer");
        JSONArray items = list == null ? null : list.optJSONArray("items");
        if (items == null) return;
        for (int i = 0; i < items.length() && out.size() < MAX_ITEMS; i++) {
            JSONObject tile = items.optJSONObject(i);
            tile = tile == null ? null : tile.optJSONObject("tileRenderer");
            if (tile == null) continue;
            String type = tile.optString("contentType", "TILE_CONTENT_TYPE_VIDEO");
            if (!"TILE_CONTENT_TYPE_VIDEO".equals(type)) continue;
            String id = tile.optString("contentId", "");
            if (id.length() != 11) id = videoId(tile.optJSONObject("onSelectCommand"));
            if (id == null) continue;
            int duration = 0;
            for (String text : texts(tile.optJSONObject("header"))) {
                int sec = durationSeconds(text);
                if (sec > 0) { duration = sec; break; }
            }
            if (duration == 0) continue;
            List<String> meta = new ArrayList<>();
            for (String text : texts(tile.optJSONObject("metadata"))) {
                if (DURATION.matcher(text).matches()) continue;
                if ("•".equals(text) || "·".equals(text)) continue;
                meta.add(text);
            }
            if (meta.isEmpty()) continue;
            String channel = meta.size() > 1 ? meta.get(1) : null;
            out.add(new Item(id, meta.get(0), channel, thumb(id), duration));
        }
    }

    private static String videoId(JSONObject command) {
        if (command == null) return null;
        JSONObject watch = command.optJSONObject("watchEndpoint");
        String id = watch == null ? null : watch.optString("videoId", "");
        return id != null && id.length() == 11 ? id : null;
    }

    private static List<String> texts(Object node) throws Exception {
        List<String> out = new ArrayList<>();
        collectTexts(node, out);
        return out;
    }

    private static void collectTexts(Object node, List<String> out) throws Exception {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            String simple = o.optString("simpleText", "");
            if (!simple.isEmpty()) out.add(simple);
            JSONArray runs = o.optJSONArray("runs");
            if (simple.isEmpty() && runs != null) {
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < runs.length(); i++) {
                    JSONObject run = runs.optJSONObject(i);
                    if (run != null) b.append(run.optString("text", ""));
                }
                if (b.length() > 0) out.add(b.toString());
            }
            JSONArray names = o.names();
            if (names == null) return;
            for (int i = 0; i < names.length(); i++) {
                String name = names.getString(i);
                if ("simpleText".equals(name) || "runs".equals(name) || "accessibility".equals(name)) continue;
                Object child = o.opt(name);
                if (child instanceof JSONObject || child instanceof JSONArray) collectTexts(child, out);
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) collectTexts(a.opt(i), out);
        }
    }

    static int durationSeconds(String label) {
        if (label == null || !DURATION.matcher(label).matches()) return 0;
        String[] p = label.split(":");
        try {
            if (p.length == 3) return Integer.parseInt(p[0]) * 3600 + Integer.parseInt(p[1]) * 60 + Integer.parseInt(p[2]);
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String thumb(String videoId) {
        return "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg";
    }

    private static String browseBody() throws Exception {
        JSONObject client = new JSONObject();
        client.put("clientName", "TVHTML5");
        client.put("clientVersion", CLIENT_VERSION);
        client.put("hl", "pt");
        client.put("gl", "BR");
        JSONObject context = new JSONObject();
        context.put("client", client);
        JSONObject body = new JSONObject();
        body.put("context", context);
        body.put("browseId", "FEwhat_to_watch");
        return body.toString();
    }

    private static String displayUrl(String url) {
        String bare = url.replace("https://", "").replace("http://", "");
        int slash = bare.indexOf('/', bare.indexOf('/') + 1);
        return slash > 0 ? bare.substring(0, slash) : bare;
    }

    private static void post(Consumer<Result> done, Result result, int gen) {
        if (gen != GENERATION.get()) return;
        mainHandler().post(() -> {
            if (gen == GENERATION.get()) done.accept(result);
        });
    }

    private static android.os.Handler mainHandler() {
        if (main == null) {
            synchronized (RecommendedProvider.class) {
                if (main == null) main = new android.os.Handler(android.os.Looper.getMainLooper());
            }
        }
        return main;
    }

    private static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String postJson(String url, String json, String bearer) throws Exception {
        HttpURLConnection c = open(url);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + bearer);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (SMART-TV; LINUX; Tizen) Cobalt/23");
        return send(c, json);
    }

    private static String postForm(String url, String form) throws Exception {
        HttpURLConnection c = open(url);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        return send(c, form);
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_TIMEOUT);
        c.setReadTimeout(READ_TIMEOUT);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        return c;
    }

    private static String send(HttpURLConnection c, String body) throws Exception {
        try {
            byte[] bytes = body.getBytes("UTF-8");
            c.setFixedLengthStreamingMode(bytes.length);
            OutputStream out = c.getOutputStream();
            out.write(bytes);
            out.close();
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) return "";
            return read(in);
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        try {
            StringBuilder b = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) b.append(buf, 0, n);
            return b.toString();
        } finally {
            r.close();
        }
    }
}
