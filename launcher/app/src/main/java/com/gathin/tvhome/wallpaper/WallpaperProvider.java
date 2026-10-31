package com.gathin.tvhome.wallpaper;

import java.time.LocalDate;

/** One source of pictures (Strategy). */
public interface WallpaperProvider {
    /** Stable id, as stored in the settings (for example {@code bing}). */
    String id();

    /**
     * The picture this source offers for {@code date}.
     *
     * @throws ProviderException on timeout, HTTP 429/5xx, bad format, a video instead of an image or a missing key
     */
    WallpaperItem fetch(LocalDate date, Http http) throws ProviderException;
}
