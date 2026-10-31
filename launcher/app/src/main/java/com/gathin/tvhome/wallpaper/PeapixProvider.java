package com.gathin.tvhome.wallpaper;

import org.json.JSONObject;

import java.time.LocalDate;

/** Peapix mirrors of Bing and Spotlight. fullUrl is 1920 wide; imageUrl (when present) is the original. */
final class PeapixProvider implements WallpaperProvider {
    static final String BING_URL = "https://peapix.com/bing/feed?country=br&n=1";
    static final String SPOTLIGHT_URL = "https://peapix.com/spotlight/feed?n=1";

    private final String id;
    private final String url;

    private PeapixProvider(String id, String url) {
        this.id = id;
        this.url = url;
    }

    static PeapixProvider bing() { return new PeapixProvider("peapix_bing", BING_URL); }

    static PeapixProvider spotlight() { return new PeapixProvider("peapix_spotlight", SPOTLIGHT_URL); }

    @Override public String id() { return id; }

    @Override
    public WallpaperItem fetch(LocalDate date, Http http) throws ProviderException {
        JSONObject entry = Json.first(Json.array(http.getText(url, null)));
        String full = Json.str(entry, "fullUrl");
        String original = Json.str(entry, "imageUrl");
        if (full.isEmpty() && original.isEmpty()) throw new ProviderException("no image url");
        return new WallpaperItem(id, Json.str(entry, "title"), full.isEmpty() ? original : full,
                original.isEmpty() ? full : original, null, Json.str(entry, "copyright"), date.toString());
    }
}
