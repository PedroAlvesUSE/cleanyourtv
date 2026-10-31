package com.gathin.tvhome.wallpaper;

import org.json.JSONObject;

import java.time.LocalDate;

/** NASA Astronomy Picture of the Day. Some days it is a video: that is an error, the aggregator moves on. */
final class NasaApodProvider implements WallpaperProvider {
    static final String DEFAULT_KEY = "DEMO_KEY";
    private static final String BASE = "https://api.nasa.gov/planetary/apod?api_key=";

    private final String key;

    NasaApodProvider(String key) {
        this.key = key == null || key.trim().isEmpty() ? DEFAULT_KEY : key.trim();
    }

    @Override public String id() { return "nasa"; }

    @Override
    public WallpaperItem fetch(LocalDate date, Http http) throws ProviderException {
        JSONObject o = Json.object(http.getText(BASE + Json.encode(key), null));
        String type = Json.str(o, "media_type");
        if (!"image".equals(type)) throw new ProviderException("APOD is not an image (" + type + ")");
        String url = Json.str(o, "url");
        String hdurl = Json.str(o, "hdurl");
        if (hdurl.isEmpty()) hdurl = url;
        if (url.isEmpty()) url = hdurl;
        if (url.isEmpty()) throw new ProviderException("no image url");
        // When the APOD service has no picture it has answered with the NASA logo.
        if (hdurl.contains("nasa-logo")) throw new ProviderException("APOD returned the NASA logo");
        String author = Json.clean(Json.str(o, "copyright"), 100);
        return new WallpaperItem(id(), Json.clean(Json.str(o, "title"), 160), url, hdurl, null,
                author.isEmpty() ? "NASA" : "© " + author, date.toString());
    }
}
