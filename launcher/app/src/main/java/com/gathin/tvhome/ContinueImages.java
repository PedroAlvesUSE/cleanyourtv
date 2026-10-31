package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Pictures for the Continue watching cards: downloaded off the main thread, kept on disk under a size cap (the
 * oldest files go first) and decoded to the card's width into a small memory cache.
 */
final class ContinueImages {
    static final long DISK_CAP = 12L * 1024 * 1024;
    static final int MAX_DOWNLOAD = 4 * 1024 * 1024;
    private static final String DIR = "continue_img";
    private static final String ART_DIR = "continue_art";

    private static final ExecutorService WORK = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> MEMORY = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };

    private ContinueImages() {
    }

    static Bitmap cached(String key) {
        return key == null ? null : MEMORY.get(key);
    }

    /** The cache key of an item's picture, or null when it has none. */
    static String keyOf(ContinueStore.Item it) {
        return it.imageFile != null ? it.imageFile : it.imageUrl;
    }

    /** Decodes (downloading first when needed) and hands the bitmap, or null, back on the main thread. */
    static void load(Context ctx, String key, int width, Consumer<Bitmap> done) {
        Context app = ctx.getApplicationContext();
        WORK.execute(() -> {
            Bitmap b = null;
            try {
                File f = key.startsWith("/") ? new File(key) : download(app, key);
                if (f != null && f.isFile()) b = decode(f, width);
            } catch (Throwable ignored) {
                // A picture that fails leaves the placeholder.
            }
            if (b != null) MEMORY.put(key, b);
            Bitmap result = b;
            MAIN.post(() -> done.accept(result));
        });
    }

    private static File download(Context ctx, String url) throws Exception {
        File dir = new File(ctx.getCacheDir(), DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        File f = new File(dir, sha1(url));
        if (f.isFile()) {
            f.setLastModified(System.currentTimeMillis());
            return f;
        }
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setInstanceFollowRedirects(true);
        try {
            if (c.getResponseCode() != 200) return null;
            File tmp = new File(dir, f.getName() + ".part");
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16 * 1024];
                int total = 0, n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_DOWNLOAD) {
                        out.close();
                        tmp.delete();
                        return null;
                    }
                    out.write(buf, 0, n);
                }
            }
            if (!tmp.renameTo(f)) return null;
        } finally {
            c.disconnect();
        }
        trim(dir, DISK_CAP);
        return f;
    }

    /** Deletes the oldest files until the folder is under cap bytes. */
    static void trim(File dir, long cap) {
        File[] files = dir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) total += f.length();
        if (total <= cap) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : files) {
            if (total <= cap) break;
            total -= f.length();
            f.delete();
        }
    }

    private static Bitmap decode(File f, int width) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= width) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    /** Saves a media session's art (scaled to at most 640 px wide) and returns the file path, or null. */
    static String saveArt(Context ctx, String name, Bitmap art) {
        if (art == null || art.getWidth() <= 0) return null;
        File dir = new File(ctx.getCacheDir(), ART_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        File f = new File(dir, sha1(name) + ".jpg");
        Bitmap scaled = art;
        if (art.getWidth() > 640) {
            scaled = Bitmap.createScaledBitmap(art, 640, Math.max(1, art.getHeight() * 640 / art.getWidth()), true);
        }
        try (FileOutputStream out = new FileOutputStream(f)) {
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out);
        } catch (Exception e) {
            return null;
        }
        MEMORY.remove(f.getPath());
        trim(dir, DISK_CAP / 3);
        return f.getPath();
    }

    static void clear(Context ctx) {
        MEMORY.evictAll();
        for (String d : new String[]{DIR, ART_DIR}) {
            File[] files = new File(ctx.getCacheDir(), d).listFiles();
            if (files != null) for (File f : files) f.delete();
        }
    }

    static String sha1(String s) {
        try {
            return Cards.hex(MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8")));
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
