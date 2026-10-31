package com.gathin.tvhome;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Continue watching from media sessions (continue_media_session). The notification access exists only so the
 * launcher may call MediaSessionManager.getActiveSessions; no notification is read. Grant it over adb:
 * {@code adb shell cmd notification allow_listener com.gathin.tvhome/.ContinueListener}. With the switch off the
 * service asks the system to unbind it and does nothing.
 */
public class ContinueListener extends NotificationListenerService {
    /** Writes for one session at most this often while it plays; a pause, a stop or new metadata write at once. */
    private static final long MIN_GAP_MS = 20_000;

    private static final Map<String, PendingIntent> SESSION_ACTIVITY = new HashMap<>();
    /** True while the system has the listener bound and watching sessions. */
    private static volatile boolean connected;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final Map<MediaController.Callback, MediaController> watched = new HashMap<>();
    private final Map<String, Long> lastWrite = new HashMap<>();
    private final Map<String, String> artFiles = new HashMap<>();
    private MediaSessionManager sessions;
    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::watch;

    static ComponentName component(Context ctx) {
        return new ComponentName(ctx, ContinueListener.class);
    }

    static boolean granted(Context ctx) {
        String list = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
        return list != null && list.contains(component(ctx).flattenToString());
    }

    static boolean enabled(Context ctx) {
        return Config.feature(ctx, ContinueStore.FEATURE) && Config.feature(ctx, ContinueStore.FEATURE_MEDIA);
    }

    /** The live session's own activity for a package, if a session of it is open; else null. */
    static synchronized PendingIntent sessionActivity(String pkg) {
        return SESSION_ACTIVITY.get(pkg);
    }

    /** After the switch changed: bind again (it turns itself off at once when the switch is off). */
    static void refresh(Context ctx) {
        if (!granted(ctx)) return;
        try {
            requestRebind(component(ctx));
        } catch (Exception ignored) {
            // Not allowed: nothing to rebind.
        }
    }

    /**
     * Binds the listener again when the switches are on but it is not running: it unbinds itself when it connects
     * with the switches off, and the system then keeps it snoozed until a rebind (a config.json change, for one).
     */
    static void ensureRunning(Context ctx) {
        if (!connected && enabled(ctx)) refresh(ctx);
    }

    @Override
    public void onListenerConnected() {
        connected = enabled(this);
        if (!connected) {
            try {
                requestUnbind();
            } catch (Exception ignored) {
                // Already gone.
            }
            return;
        }
        try {
            sessions = getSystemService(MediaSessionManager.class);
            ComponentName me = component(this);
            sessions.addOnActiveSessionsChangedListener(changed, me, main);
            watch(sessions.getActiveSessions(me));
        } catch (SecurityException e) {
            sessions = null;
        }
    }

    @Override
    public void onListenerDisconnected() {
        connected = false;
        stop();
    }

    @Override
    public void onDestroy() {
        connected = false;
        stop();
        work.shutdown();
        super.onDestroy();
    }

    private void stop() {
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(changed);
        sessions = null;
        for (Map.Entry<MediaController.Callback, MediaController> e : watched.entrySet()) {
            e.getValue().unregisterCallback(e.getKey());
        }
        watched.clear();
        synchronized (ContinueListener.class) {
            SESSION_ACTIVITY.clear();
        }
    }

    private void watch(List<MediaController> list) {
        for (Map.Entry<MediaController.Callback, MediaController> e : watched.entrySet()) {
            e.getValue().unregisterCallback(e.getKey());
        }
        watched.clear();
        synchronized (ContinueListener.class) {
            SESSION_ACTIVITY.clear();
        }
        if (list == null) return;
        for (MediaController c : list) {
            String pkg = c.getPackageName();
            if (pkg == null || pkg.equals(getPackageName())) continue;
            synchronized (ContinueListener.class) {
                if (c.getSessionActivity() != null) SESSION_ACTIVITY.put(pkg, c.getSessionActivity());
            }
            MediaController.Callback cb = new MediaController.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadata metadata) {
                    record(c, true);
                }

                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    record(c, state != null && state.getState() != PlaybackState.STATE_PLAYING);
                }

                @Override
                public void onSessionDestroyed() {
                    record(c, true);
                }
            };
            c.registerCallback(cb, main);
            watched.put(cb, c);
            record(c, true);
        }
    }

    private void record(MediaController c, boolean now) {
        if (!enabled(this)) return;
        MediaMetadata m = c.getMetadata();
        if (m == null) return;
        String pkg = c.getPackageName();
        String title = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        String display = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String key = pkg + '\n' + (display != null ? display : title);
        long t = SystemClock.elapsedRealtime();
        Long last = lastWrite.get(key);
        if (!now && last != null && t - last < MIN_GAP_MS) return;
        lastWrite.put(key, t);
        long duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long position = position(c.getPlaybackState());
        String artUri = firstText(m, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        Bitmap art = ContinueStore.imageUrl(artUri) != null ? null : firstBitmap(m);
        String subtitle = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        String artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        String album = m.getString(MediaMetadata.METADATA_KEY_ALBUM);
        Context app = getApplicationContext();
        work.execute(() -> {
            String file = artFiles.get(key);
            if (art != null && file == null) {
                file = ContinueImages.saveArt(app, key, art);
                if (file != null) artFiles.put(key, file);
            }
            ContinueStore.Item item = ContinueStore.fromMedia(pkg, display, title, subtitle, artist, album, artUri,
                    file, position, duration, System.currentTimeMillis());
            if (item != null) ContinueProvider.upsert(app, item);
        });
    }

    /** The position now: the last reported one, moved on by the time since and the speed while playing. */
    static long position(PlaybackState s) {
        if (s == null) return 0;
        long pos = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING && s.getLastPositionUpdateTime() > 0) {
            pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
        }
        return Math.max(0, pos);
    }

    private static String firstText(MediaMetadata m, String... keys) {
        for (String k : keys) {
            String v = m.getString(k);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    private static Bitmap firstBitmap(MediaMetadata m) {
        for (String k : new String[]{MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
            Bitmap b = m.getBitmap(k);
            if (b != null) return b;
        }
        return null;
    }
}
