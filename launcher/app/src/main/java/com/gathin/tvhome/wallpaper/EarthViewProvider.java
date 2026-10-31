package com.gathin.tvhome.wallpaper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;

/** Google Earth View: a static list, one entry a day chosen by {@link DailySeed}. */
final class EarthViewProvider implements WallpaperProvider {
    static final String URL = "https://raw.githubusercontent.com/limhenry/earthview/master/earthview.json";

    private final CatalogCache cache;

    EarthViewProvider(CatalogCache cache) {
        this.cache = cache;
    }

    @Override public String id() { return "earthview"; }

    @Override
    public WallpaperItem fetch(LocalDate date, Http http) throws ProviderException {
        JSONArray list = Json.array(CatalogCache.load(cache, "earthview", URL, null, http));
        int n = list.length();
        if (n == 0) throw new ProviderException("empty catalog");
        int start = DailySeed.index(date, "earthview", n);
        for (int i = 0; i < n; i++) {
            JSONObject entry = list.optJSONObject((start + i) % n);
            String image = Json.str(entry, "image");
            if (!image.startsWith("http")) continue;
            String country = Json.str(entry, "country");
            String region = Json.str(entry, "region");
            String title = region.isEmpty() ? country : country.isEmpty() ? region : region + ", " + country;
            return new WallpaperItem(id(), title.isEmpty() ? "Earth View" : title, image, image, null,
                    "Google Earth View", date.toString());
        }
        throw new ProviderException("no usable entry");
    }
}
