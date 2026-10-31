package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.security.SecureRandom;

/**
 * Controle pelo celular (phone_remote): página web na rede local com D-pad, OK, Voltar, Home, volume, texto
 * e envio de link. Desligado por padrão. Só roda com a opção ligada e o serviço de acessibilidade ativo (o
 * HomeKeyService liga e desliga o servidor). Exige o PIN de 4 dígitos mostrado na TV.
 */
final class RemoteServer implements LanServer.Handler {
    static final String FEATURE = "phone_remote";
    static final String PIN_KEY = "phone_remote_pin";
    static final int PORT = 8765;

    private static RemoteServer current;

    private final Context app;
    private final PinGate gate;
    private final LanServer server = new LanServer(this);
    private final Handler main = new Handler(Looper.getMainLooper());
    private int port;

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

    @Override
    public LanServer.Response handle(LanServer.Request r) {
        if (r.method.equals("GET") && r.path.equals("/")) return LanServer.Response.text(200, "text/html", WebPages.remote());
        if (!r.path.startsWith("/api/")) return LanServer.Response.error(404, "not found");
        if (!gate.check(r.headers.get("x-pin"), System.currentTimeMillis())) {
            return LanServer.Response.error(gate.locked(System.currentTimeMillis()) ? 429 : 401, "pin");
        }
        if (r.path.equals("/api/ping")) return LanServer.Response.ok("ok");
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
        return LanServer.Response.error(404, "not found");
    }
}
