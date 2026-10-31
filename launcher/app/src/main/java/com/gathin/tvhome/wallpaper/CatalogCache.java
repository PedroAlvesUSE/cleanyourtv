package com.gathin.tvhome.wallpaper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Static catalogs (Chromecast, Earth View, Met ids) kept on disk for 7 days; they change rarely and are large. */
public final class CatalogCache {
    public static final long TTL_MS = 7L * 24 * 60 * 60 * 1000;

    private final File dir;

    /** @param dir for example {@code new File(filesDir, "wallpaper_catalog")}; created on the first write */
    public CatalogCache(File dir) {
        this.dir = dir;
    }

    /** The cached text, or null when there is none or it is older than the TTL. */
    public String get(String name) {
        return get(name, System.currentTimeMillis());
    }

    public String get(String name, long nowMs) {
        if (dir == null) return null;
        File file = file(name);
        if (!file.isFile() || nowMs - file.lastModified() > TTL_MS) return null;
        return read(file);
    }

    /** The cached text whatever its age (used when the network fails), or null. */
    public String getStale(String name) {
        if (dir == null) return null;
        File file = file(name);
        return file.isFile() ? read(file) : null;
    }

    /** Best effort: a failed write only means the next call fetches again. */
    public void put(String name, String text) {
        if (dir == null || text == null) return;
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        File target = file(name);
        File tmp = new File(dir, target.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(target)) {
            target.delete();
            if (!tmp.renameTo(target)) tmp.delete();
        }
    }

    /**
     * Fresh cache, else the network (then cached), else a stale cache, else the error. {@code cache} may be null.
     */
    static String load(CatalogCache cache, String name, String url, Map<String, String> headers, Http http)
            throws ProviderException {
        if (cache != null) {
            String fresh = cache.get(name);
            if (fresh != null) return fresh;
        }
        try {
            String text = http.getText(url, headers);
            if (cache != null) cache.put(name, text);
            return text;
        } catch (ProviderException e) {
            String stale = cache == null ? null : cache.getStale(name);
            if (stale != null) return stale;
            throw e;
        }
    }

    private File file(String name) {
        return new File(dir, name.replaceAll("[^A-Za-z0-9_.-]", "_") + ".json");
    }

    private static String read(File file) {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
