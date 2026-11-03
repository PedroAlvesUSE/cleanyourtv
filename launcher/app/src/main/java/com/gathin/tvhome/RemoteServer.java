package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controle pelo celular (phone_remote): página web na rede local com touchpad (setas e OK), Voltar, Home,
 * volume, mudo, teclas de mídia, busca no YouTube, lista de apps, texto e envio de link. Desligado por padrão. Só roda com a opção ligada e o serviço de acessibilidade ativo (o
 * HomeKeyService liga e desliga o servidor). Exige o PIN de 4 dígitos mostrado na TV.
 */
final class RemoteServer implements LanServer.Handler {
    static final String FEATURE = "phone_remote";
    static final String PIN_KEY = "phone_remote_pin";
    static final int PORT = 8765;
    private static final int ICON_PX = 96;
    private static final long APPS_TTL_MS = 30_000;

    private static RemoteServer current;

    private final Context app;
    private final PinGate gate;
    private final LanServer server = new LanServer(this);
    private final Handler main = new Handler(Looper.getMainLooper());
    private int port;
    private Map<String, String> apps;
    private long appsAt;

    private RemoteServer(Context app, String pin) {
        this.app = app;
        this.gate = new PinGate(pin);
    }

    /** O PIN guardado, ou um novo se ainda não existe. */
    static String pin(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        String pin = p.getString(PIN_KEY, null);
        if (pin == null || !PinHash.valid(pin)) {
            pin = String.format("%04d", new SecureRandom().nextInt(10000));
            p.edit().putString(PIN_KEY, pin).apply();
        }
        return pin;
    }

    static synchronized boolean start(Context ctx) {
        if (current != null) return true;
        Context app = ctx.getApplicationContext();
        RemoteServer s = new RemoteServer(app, pin(app));
        try {
            s.port = s.server.start(PORT);
        } catch (IOException e) {
            return false;
        }
        current = s;
        return true;
    }

    static synchronized void stop() {
        if (current != null) current.server.stop();
        current = null;
    }

    /** Novo PIN; o servidor volta a rodar com ele. */
    static synchronized String renewPin(Context ctx) {
        boolean wasOn = current != null;
        stop();
        ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit().remove(PIN_KEY).apply();
        if (wasOn) start(ctx);
        return pin(ctx);
    }

    static synchronized boolean running() {
        return current != null;
    }

    /** "http://192.168.3.21:8765" ou null se o servidor está desligado ou a TV está sem rede. */
    static synchronized String url() {
        String ip = LanServer.localIp();
        return current == null || ip == null ? null : "http://" + ip + ":" + current.port;
    }

    /** O nome da TV nas configurações (device_name), senão o nome Bluetooth, senão o modelo. */
    static String tvName(Context ctx) {
        return RemoteApi.tvName(setting(ctx, true, Settings.Global.DEVICE_NAME), setting(ctx, false, "bluetooth_name"), Build.MODEL);
    }

    private static String setting(Context ctx, boolean global, String key) {
        try {
            return global ? Settings.Global.getString(ctx.getContentResolver(), key)
                    : Settings.Secure.getString(ctx.getContentResolver(), key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A URL do QR, que já leva o PIN; null se o servidor está desligado ou sem rede. */
    static String pairUrl(Context ctx) {
        return RemoteApi.pairUrl(url(), pin(ctx));
    }

    @Override
    public LanServer.Response handle(LanServer.Request r) {
        if (r.method.equals("GET") && r.path.equals("/")) return LanServer.Response.text(200, "text/html", WebPages.remote());
        if (!r.path.startsWith("/api/")) return LanServer.Response.error(404, "not found");
        if (!gate.check(r.headers.get("x-pin"), System.currentTimeMillis())) {
            return LanServer.Response.error(gate.locked(System.currentTimeMillis()) ? 429 : 401, "pin");
        }
        if (r.path.equals("/api/ping")) return LanServer.Response.ok("ok");
        if (r.path.equals("/api/info") && r.method.equals("GET")) {
            return LanServer.Response.text(200, "application/json", RemoteApi.infoJson(tvName(app), HomeKeyService.extraKeys(app)));
        }
        HomeKeyService service = HomeKeyService.instance();
        if (r.path.equals("/api/key") && r.method.equals("POST")) {
            String key = r.query.get("k");
            if (key == null || !HomeKeyService.REMOTE_KEYS.contains(key)) return LanServer.Response.error(400, "key");
            main.post(() -> {
                HomeKeyService s = HomeKeyService.instance();
                if (s != null) s.remoteKey(key);
            });
            return LanServer.Response.ok(service == null ? "off" : "ok");
        }
        if (r.path.equals("/api/text") && r.method.equals("POST")) {
            String text = r.bodyText();
            if (text.length() > 500) return LanServer.Response.error(400, "text");
            main.post(() -> {
                HomeKeyService s = HomeKeyService.instance();
                if (s != null) s.remoteText(text);
            });
            return LanServer.Response.ok("ok");
        }
        if (r.path.equals("/api/link") && r.method.equals("POST")) {
            String url = r.bodyText().trim();
            if (url.length() > 2000 || !url.matches("(?i)https?://[^\\s]+")) return LanServer.Response.error(400, "link");
            main.post(() -> MicSearch.openLink(app, url));
            return LanServer.Response.ok("ok");
        }
        if (r.path.equals("/api/search") && r.method.equals("POST")) {
            String term = RemoteApi.searchTerm(r.bodyText());
            if (term == null) return LanServer.Response.error(400, "search");
            main.post(() -> MicSearch.open(app, term));
            return LanServer.Response.ok("ok");
        }
        if (r.path.equals("/api/apps") && r.method.equals("GET")) {
            List<String[]> list = new ArrayList<>();
            for (Map.Entry<String, String> e : apps().entrySet()) list.add(new String[]{e.getKey(), e.getValue()});
            return LanServer.Response.text(200, "application/json", RemoteApi.appsJson(list));
        }
        if (r.path.equals("/api/icon") && r.method.equals("GET")) {
            String pkg = r.query.get("p");
            if (!RemoteApi.validPackage(pkg) || !apps().containsKey(pkg)) return LanServer.Response.error(404, "app");
            byte[] png = icon(pkg);
            return png == null ? LanServer.Response.error(404, "icon") : new LanServer.Response(200, "image/png", png);
        }
        if (r.path.equals("/api/open") && r.method.equals("POST")) {
            String pkg = r.query.get("p");
            if (!RemoteApi.validPackage(pkg) || !apps().containsKey(pkg)) return LanServer.Response.error(404, "app");
            main.post(() -> RemoteKeys.launch(app, pkg));
            return LanServer.Response.ok("ok");
        }
        return LanServer.Response.error(404, "not found");
    }

    /**
     * Os apps da grade do launcher (TV mais os adicionados, sem os ocultos e os removidos), em ordem de nome.
     * Com o controle parental ligado, os apps protegidos ficam fora. Guardado por 30 s.
     */
    private synchronized Map<String, String> apps() {
        long now = SystemClock.elapsedRealtime();
        if (apps != null && now - appsAt < APPS_TTL_MS) return apps;
        PackageManager pm = app.getPackageManager();
        Config config = Config.load(app);
        Set<String> hidden = new HashSet<>(config.hiddenApps);
        if (config.feature(Parental.FEATURE) && Parental.hasPin(app)) hidden.addAll(Parental.apps(app));
        Set<String> added = LibraryApps.load(app);
        Set<String> removed = LibraryApps.loadRemoved(app);
        Set<String> tv = new HashSet<>();
        List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        for (ResolveInfo ri : found) tv.add(ri.activityInfo.packageName);
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            if (added.contains(ri.activityInfo.packageName)) found.add(ri);
        }
        List<String[]> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(app.getPackageName()) || !ri.activityInfo.exported || !seen.add(pkg)
                    || !LibraryApps.visible(tv.contains(pkg), hidden.contains(pkg), added.contains(pkg), removed.contains(pkg))) continue;
            list.add(new String[]{pkg, Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg)});
        }
        list.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
        Map<String, String> map = new LinkedHashMap<>();
        for (String[] a : list) map.put(a[0], a[1]);
        apps = map;
        appsAt = now;
        return map;
    }

    /** O ícone do app em PNG de 96 px. */
    private byte[] icon(String pkg) {
        try {
            Drawable d = app.getPackageManager().getApplicationIcon(pkg);
            Bitmap b = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, ICON_PX, ICON_PX);
            d.draw(new Canvas(b));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
            b.recycle();
            return out.toByteArray();
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }
}
