package com.gathin.tvhome;

import android.view.KeyEvent;

import java.util.List;

/** Regras puras do controle pelo celular: URL do QR com o PIN, teclas de mídia, validação e JSON da lista de apps. */
final class RemoteApi {
    static final int MAX_SEARCH = 200;
    static final int MAX_NAME = 40;

    private RemoteApi() { }

    /** "http://ip:porta/#pin=1234": o fragmento não sai do celular; a página lê o PIN e o apaga da barra. */
    static String pairUrl(String base, String pin) {
        if (base == null || pin == null) return null;
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return b + "/#pin=" + pin;
    }

    /** Keycode de mídia de um botão da página, ou 0 se não é tecla de mídia. */
    static int mediaKeyCode(String key) {
        if (key == null) return 0;
        switch (key) {
            case "playpause": return KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
            case "next": return KeyEvent.KEYCODE_MEDIA_NEXT;
            case "prev": return KeyEvent.KEYCODE_MEDIA_PREVIOUS;
            case "rewind": return KeyEvent.KEYCODE_MEDIA_REWIND;
            case "forward": return KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
            default: return 0;
        }
    }

    /** Nome de pacote Android válido (a.b, letras, dígitos e _), até 255 caracteres. */
    static boolean validPackage(String p) {
        return p != null && p.length() <= 255 && p.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    }

    /** Termo de busca limpo, ou null se vazio, longo demais ou com quebra de linha. */
    static String searchTerm(String body) {
        if (body == null) return null;
        String t = body.trim();
        if (t.isEmpty() || t.length() > MAX_SEARCH || t.indexOf('\n') >= 0 || t.indexOf('\r') >= 0) return null;
        return t;
    }

    /**
     * O nome da TV: o primeiro não vazio entre device_name, bluetooth_name e o modelo, sem caracteres de
     * controle e com até 40 caracteres. "TV" se nenhum serve.
     */
    static String tvName(String... candidates) {
        for (String c : candidates) {
            if (c == null) continue;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < c.length(); i++) {
                char ch = c.charAt(i);
                sb.append(Character.isISOControl(ch) || ch == 0x2028 || ch == 0x2029 ? ' ' : ch);
            }
            String t = sb.toString().trim().replaceAll(" +", " ");
            if (t.isEmpty()) continue;
            return t.length() > MAX_NAME ? t.substring(0, MAX_NAME).trim() : t;
        }
        return "TV";
    }

    /** {"name":"Quarto","keys":["input","sleep"]}: o nome da TV e os botões extras que esta TV aceita. */
    static String infoJson(String name, List<String> keys) {
        StringBuilder sb = new StringBuilder("{\"name\":").append(json(name)).append(",\"keys\":[");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(json(keys.get(i)));
        }
        return sb.append("]}").toString();
    }

    /** [{"p":"pacote","n":"nome"}, ...] a partir de pares {pacote, nome}. */
    static String appsJson(List<String[]> apps) {
        StringBuilder sb = new StringBuilder("[");
        for (String[] a : apps) {
            if (sb.length() > 1) sb.append(',');
            sb.append("{\"p\":").append(json(a[0])).append(",\"n\":").append(json(a[1])).append('}');
        }
        return sb.append(']').toString();
    }

    static String json(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20 || c == '<' || c == '>' || c == '&' || c == 0x2028 || c == 0x2029) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }
}
