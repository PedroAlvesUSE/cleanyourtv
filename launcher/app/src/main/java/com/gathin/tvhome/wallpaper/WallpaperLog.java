package com.gathin.tvhome.wallpaper;

import android.util.Log;

/** Log.w that never throws: on a plain JVM test the android.jar stub raises "not mocked". */
final class WallpaperLog {
    private static final String TAG = "TvHomeWallpaper";

    private WallpaperLog() { }

    static void warn(String message) {
        try {
            Log.w(TAG, message);
        } catch (Throwable ignored) {
            // JVM unit test: no Android runtime
        }
    }
}
