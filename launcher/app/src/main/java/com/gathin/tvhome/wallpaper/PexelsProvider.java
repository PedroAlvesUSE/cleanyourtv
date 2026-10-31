package com.gathin.tvhome.wallpaper;

import org.json.JSONObject;

import java.time.LocalDate;

/** Pexels curated photo. Needs an API key; without one it fails with "no key". */
final class PexelsProvider implements WallpaperProvider {
    static final String URL = "https://api.pexels.com/v1/curated?per_page=1";

    private final String key;

    PexelsProvider(String key) {
        this.key = key == null ? "" : key.trim();
    }

    @Override public String id() { return "pexels"; }

    @Override
    public WallpaperItem fetch(LocalDate date, Http http) throws ProviderException {
        if (key.isEmpty()) throw new ProviderException("no key");
        JSONObject photo = Json.first(Json.arr(Json.object(http.getText(URL, Json.headers("Authorization", key))), "photos"));
        JSONObject src = Json.obj(photo, "src");
        String original = Json.str(src, "original");
        if (original.isEmpty()) throw new ProviderException("no original url");
        // The original can be 20 MB or more; the Pexels CDN resizes it on request.
        String join = original.contains("?") ? "&" : "?";
        String photographer = Json.str(photo, "photographer");
        return new WallpaperItem(id(), Json.clean(Json.str(photo, "alt"), 160),
                original + join + "auto=compress&cs=tinysrgb&fit=crop&w=1920&h=1080",
                original + join + "auto=compress&cs=tinysrgb&fit=crop&w=3840&h=2160", null,
                photographer.isEmpty() ? "Pexels" : "Photo by " + photographer + " on Pexels", date.toString());
    }
}
