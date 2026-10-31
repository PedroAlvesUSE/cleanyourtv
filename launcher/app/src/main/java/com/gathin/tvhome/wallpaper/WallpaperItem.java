package com.gathin.tvhome.wallpaper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One picture of the day from one source. Null texts become empty strings; {@code urlPortrait} may stay null. */
public final class WallpaperItem {
    public final String provider;
    public final String title;
    public final String urlHd;
    public final String urlUhd;
    public final String urlPortrait;
    public final String copyright;
    /** YYYY-MM-DD */
    public final String date;
    /** Headers the image download must send (a few sources refuse requests without one); never null. */
    public final Map<String, String> imageHeaders;

    public WallpaperItem(String provider, String title, String urlHd, String urlUhd, String urlPortrait,
                         String copyright, String date) {
        this(provider, title, urlHd, urlUhd, urlPortrait, copyright, date, null);
    }

    public WallpaperItem(String provider, String title, String urlHd, String urlUhd, String urlPortrait,
                         String copyright, String date, Map<String, String> imageHeaders) {
        this.provider = provider == null ? "" : provider;
        this.title = title == null ? "" : title;
        this.urlHd = urlHd == null ? "" : urlHd;
        this.urlUhd = urlUhd == null ? "" : urlUhd;
        this.urlPortrait = urlPortrait == null || urlPortrait.isEmpty() ? null : urlPortrait;
        this.copyright = copyright == null ? "" : copyright;
        this.date = date == null ? "" : date;
        this.imageHeaders = imageHeaders == null || imageHeaders.isEmpty()
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(imageHeaders));
    }

    /** True when at least one of the landscape URLs is an http(s) address. */
    public boolean hasImage() {
        return isHttp(urlUhd) || isHttp(urlHd);
    }

    private static boolean isHttp(String url) {
        return url.startsWith("https://") || url.startsWith("http://");
    }

    @Override
    public String toString() {
        return provider + " " + date + " \"" + title + "\" " + urlUhd;
    }
}
