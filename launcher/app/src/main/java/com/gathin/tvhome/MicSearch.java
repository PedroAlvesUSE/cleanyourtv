package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

/**
 * Botão de microfone do controle abre a busca no TizenTube (mic_tizentube). O TizenTube aceita
 * https://www.youtube.com/results?search_query=TERMO com o app já aberto; na partida a frio ele ignora a URL
 * e abre a Home. Por isso a URL é enviada de novo depois que o app termina de carregar.
 */
final class MicSearch {
    static final String TIZENTUBE = "io.gh.reisxd.tizentube.cobalt";
    static final String CONFIG_KEY = "mic_keycodes";
    /** KEYCODE_ASSIST (o controle BLE da TCL), KEYCODE_VOICE_ASSIST e KEYCODE_SEARCH. */
    static final int[] DEFAULT_KEYCODES = {219, 231, 84};
    private static final long COLD_START_RESEND_MS = 8000;

    private MicSearch() { }

    /** Lista de códigos positivos; qualquer item inválido faz a lista inteira voltar ao padrão. */
    static int[] keycodes(Object value) {
        if (!(value instanceof JSONArray)) return DEFAULT_KEYCODES;
        JSONArray a = (JSONArray) value;
        if (a.length() == 0) return DEFAULT_KEYCODES;
        int[] out = new int[a.length()];
        for (int i = 0; i < out.length; i++) {
            Object item = a.opt(i);
            if (!(item instanceof Number)) return DEFAULT_KEYCODES;
            out[i] = ((Number) item).intValue();
            if (out[i] < 1) return DEFAULT_KEYCODES;
        }
        return out;
    }

    static boolean contains(int[] codes, int keyCode) {
        for (int c : codes) if (c == keyCode) return true;
        return false;
    }

    /** URL de busca do YouTube; vazia se não houver termo. */
    static String searchUrl(String term) {
        String t = term == null ? "" : term.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) return "";
        try {
            return "https://www.youtube.com/results?search_query=" + URLEncoder.encode(t, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private static Intent view(String url) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(TIZENTUBE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    /** Abre a busca no TizenTube. Sem termo ou sem URL aceita, só abre o app. */
    static boolean open(Context ctx, String term) {
        Context app = ctx.getApplicationContext();
        String url = searchUrl(term);
        if (url.isEmpty()) {
            RemoteKeys.launch(app, TIZENTUBE);
            return false;
        }
        boolean cold = !running(app);
        try {
            app.startActivity(view(url));
        } catch (ActivityNotFoundException e) {
            return false;
        }
        if (cold) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    app.startActivity(view(url));
                } catch (ActivityNotFoundException ignored) {
                }
            }, COLD_START_RESEND_MS);
        }
        return true;
    }

    /** Abre um link (envio pelo celular): no TizenTube se for do YouTube, senão em qualquer app. */
    static boolean openLink(Context ctx, String url) {
        Uri uri = Uri.parse(url);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        boolean youtube = host.equals("youtu.be") || host.equals("youtube.com") || host.endsWith(".youtube.com");
        Intent open = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (youtube) {
                boolean cold = !running(ctx);
                ctx.startActivity(view(url));
                if (cold) {
                    Context app = ctx.getApplicationContext();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            app.startActivity(view(url));
                        } catch (ActivityNotFoundException ignored) {
                        }
                    }, COLD_START_RESEND_MS);
                }
            } else {
                ctx.startActivity(open);
            }
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    /** O processo do TizenTube existe (aberto ou em segundo plano). */
    static boolean running(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> list = am == null ? null : am.getRunningAppProcesses();
        if (list == null) return false;
        for (ActivityManager.RunningAppProcessInfo p : list) if (TIZENTUBE.equals(p.processName)) return true;
        return false;
    }
}
