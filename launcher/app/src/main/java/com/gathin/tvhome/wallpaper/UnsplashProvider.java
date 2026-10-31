package com.gathin.tvhome.wallpaper;

import org.json.JSONObject;

import java.time.LocalDate;

/** Unsplash random landscape photo. Needs an access key; without one it fails with "no key". */
final class UnsplashProvider implements WallpaperProvider {
    static final String URL = "https://api.unsplash.com/photos/random?orientation=landscape";

    private final String key;

    UnsplashProvider(String key) {
        this.key = key == null ? "" : key.trim();
    }

    @Override public String id() { return "unsplash"; }

    @Override
    public WallpaperItem fetch(LocalDate date, Http http) throws ProviderException {
        if (key.isEmpty()) throw new ProviderException("no key");
        JSONObject photo = Json.object(http.getText(URL, Json.headers("Authorization", "Client-ID " + key)));
        JSONObject urls = Json.obj(photo, "urls");
        String raw = Json.str(urls, "raw");
        if (raw.isEmpty()) throw new ProviderException("no raw url");
        String join = raw.contains("?") ? "&" : "?";
        String title = Json.str(photo, "description");
        if (title.isEmpty()) title = Json.str(photo, "alt_description");
        String user = Json.str(Json.obj(photo, "user"), "name");
        return new WallpaperItem(id(), Json.clean(title, 160), raw + join + "w=1920&fm=jpg&q=80",
                raw + join + "w=3840&fm=jpg&q=80", null,
                user.isEmpty() ? "Unsplash" : "Photo by " + user + " on Unsplash", date.toString());
    }
}
