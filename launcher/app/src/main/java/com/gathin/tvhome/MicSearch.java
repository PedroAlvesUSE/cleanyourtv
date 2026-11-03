package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.json.JSONArray;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Botão de microfone do controle abre a busca no YouTube (mic_youtube). Links e buscas
 * do controle pelo celular usam o mesmo caminho: YouTube TV quando instalado, senão o
 * resolvedor do sistema.
 */
final class MicSearch {
    static final String YOUTUBE_TV = "com.google.android.youtube.tv";
    static final String CONFIG_KEY = "mic_keycodes";
    /** KEYCODE_ASSIST (o controle BLE da TCL), KEYCODE_VOICE_ASSIST e KEYCODE_SEARCH. */
    static final int[] DEFAULT_KEYCODES = {219, 231, 84};

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
        return new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    private static boolean installed(Context ctx, String pkg) {
        try {
            ctx.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Abre a busca no YouTube. Sem termo, só abre o YouTube TV. */
    static boolean open(Context ctx, String term) {
        Context app = ctx.getApplicationContext();
        String url = searchUrl(term);
        if (url.isEmpty()) {
            RemoteKeys.launch(app, YOUTUBE_TV);
            return false;
        }
        try {
            Intent i = view(url);
            if (installed(app, YOUTUBE_TV)) i.setPackage(YOUTUBE_TV);
            app.startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    /** Abre um link (envio pelo celular): YouTube TV para links do YouTube, senão qualquer app. */
    static boolean openLink(Context ctx, String url) {
        Uri uri = Uri.parse(url);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        boolean youtube = host.equals("youtu.be") || host.equals("youtube.com") || host.endsWith(".youtube.com");
        Intent open = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (youtube && installed(ctx, YOUTUBE_TV)) open.setPackage(YOUTUBE_TV);
            ctx.startActivity(open);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
