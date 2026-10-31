package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Backup das configurações (config_backup): config.json mais as escolhas feitas na TV (launcher_settings)
 * num só JSON. Os PINs e o temporizador nunca entram no arquivo, nem a importação os aceita.
 */
final class BackupData {
    static final int VERSION = 1;
    static final int MAX_BYTES = 256 * 1024;
    private static final int MAX_ENTRIES = 500;
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    /** Segredos e estado temporário: ficam de fora da exportação e a importação os ignora. */
    static final Set<String> PRIVATE = new HashSet<>(Arrays.asList(
            Parental.PIN_KEY, RemoteServer.PIN_KEY, SleepTimer.DEADLINE_KEY, SleepTimer.MINUTES_KEY));

    private BackupData() { }

    /** O conteúdo lido de um backup, já validado. */
    static final class Parsed {
        /** config.json como texto; null se o backup não trouxe. */
        final String config;
        final Map<String, Object> settings;

        Parsed(String config, Map<String, Object> settings) {
            this.config = config;
            this.settings = settings;
        }
    }

    static String export(String configText, Map<String, ?> prefs) {
        try {
            JSONObject out = new JSONObject();
            out.put("app", "cleanyourtv");
            out.put("version", VERSION);
            if (configText != null) {
                try {
                    out.put("config", new JSONObject(configText));
                } catch (JSONException ignored) {
                    // um config.json com defeito não vai para o backup
                }
            }
            JSONObject settings = new JSONObject();
            for (Map.Entry<String, ?> e : prefs.entrySet()) {
                Object v = e.getValue();
                if (PRIVATE.contains(e.getKey()) || !KEY.matcher(e.getKey()).matches()) continue;
                if (v instanceof Set) {
                    JSONArray a = new JSONArray();
                    for (Object s : (Set<?>) v) a.put(String.valueOf(s));
                    settings.put(e.getKey(), a);
                } else if (v instanceof Boolean || v instanceof Number || v instanceof String) {
                    settings.put(e.getKey(), v);
                }
            }
            out.put("settings", settings);
            return out.toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Valida o texto de um backup. Lança IllegalArgumentException com o motivo se algo estiver errado. */
    static Parsed parse(String text) {
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("empty");
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("too large");
        JSONObject root;
        try {
            root = new JSONObject(text);
        } catch (JSONException e) {
            throw new IllegalArgumentException("not json");
        }
        if (!"cleanyourtv".equals(root.optString("app")) || root.optInt("version", -1) != VERSION) {
            throw new IllegalArgumentException("wrong app or version");
        }
        String config = null;
        if (root.has("config")) {
            Object c = root.opt("config");
            if (!(c instanceof JSONObject)) throw new IllegalArgumentException("config must be an object");
            config = c.toString();
        }
        Object s = root.opt("settings");
        if (!(s instanceof JSONObject)) throw new IllegalArgumentException("settings must be an object");
        JSONObject settings = (JSONObject) s;
        if (settings.length() > MAX_ENTRIES) throw new IllegalArgumentException("too many settings");
        Map<String, Object> out = new LinkedHashMap<>();
        for (java.util.Iterator<String> it = settings.keys(); it.hasNext(); ) {
            String key = it.next();
            if (!KEY.matcher(key).matches()) throw new IllegalArgumentException("bad key " + key);
            if (PRIVATE.contains(key)) continue;
            Object v = settings.opt(key);
            if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
                out.put(key, v);
            } else if (v instanceof Number) {
                out.put(key, ((Number) v).floatValue());
            } else if (v instanceof String && ((String) v).length() <= 4096) {
                out.put(key, v);
            } else if (v instanceof JSONArray && ((JSONArray) v).length() <= 200) {
                Set<String> set = new LinkedHashSet<>();
                JSONArray a = (JSONArray) v;
                for (int i = 0; i < a.length(); i++) {
                    Object item = a.opt(i);
                    if (!(item instanceof String) || ((String) item).length() > 256) {
                        throw new IllegalArgumentException("bad list " + key);
                    }
                    set.add((String) item);
                }
                out.put(key, set);
            } else {
                throw new IllegalArgumentException("bad value " + key);
            }
        }
        return new Parsed(config, out);
    }

    /** Texto do backup desta TV. */
    static String exportNow(Context ctx) {
        String config = null;
        File dir = Config.dir(ctx);
        if (dir != null) {
            File f = new File(dir, Config.FILE_NAME);
            if (f.isFile() && f.length() <= MAX_BYTES) {
                try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                    byte[] b = new byte[(int) f.length()];
                    int n = 0;
                    while (n < b.length) {
                        int r = in.read(b, n, b.length - n);
                        if (r < 0) break;
                        n += r;
                    }
                    config = new String(b, 0, n, StandardCharsets.UTF_8);
                } catch (IOException ignored) {
                }
            }
        }
        return export(config, ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getAll());
    }

    /** Aplica um backup já validado: troca as escolhas da TV (menos os segredos) e o config.json. */
    @SuppressWarnings("unchecked")
    static void apply(Context ctx, Parsed backup) throws IOException {
        if (backup.config != null) {
            File dir = Config.dir(ctx);
            if (dir == null) throw new IOException("no config folder");
            File tmp = new File(dir, Config.FILE_NAME + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(backup.config.getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(new File(dir, Config.FILE_NAME))) throw new IOException("rename failed");
        }
        SharedPreferences prefs = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        for (String key : prefs.getAll().keySet()) if (!PRIVATE.contains(key)) edit.remove(key);
        for (Map.Entry<String, Object> e : backup.settings.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Boolean) edit.putBoolean(e.getKey(), (Boolean) v);
            else if (v instanceof Integer) edit.putInt(e.getKey(), (Integer) v);
            else if (v instanceof Long) edit.putLong(e.getKey(), (Long) v);
            else if (v instanceof Float) edit.putFloat(e.getKey(), (Float) v);
            else if (v instanceof String) edit.putString(e.getKey(), (String) v);
            else if (v instanceof Set) edit.putStringSet(e.getKey(), (Set<String>) v);
        }
        edit.apply();
    }
}
