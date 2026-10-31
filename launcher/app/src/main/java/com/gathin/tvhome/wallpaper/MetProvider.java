package com.gathin.tvhome.wallpaper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;

/**
 * The Met, public domain landscapes. The v1 search was retired on 2026-10-01; v1.1 is paginated (500 ids at most),
 * so the first page of ids is cached and the day's seed picks one. Objects without a picture are skipped.
 */
final class MetProvider implements WallpaperProvider {
    static final String SEARCH_URL = "https://collectionapi.metmuseum.org/public/collection/v1.1/search"
            + "?hasImages=true&isPublicDomain=true&q=landscape&limit=500";
    static final String OBJECT_URL = "https://collectionapi.metmuseum.org/public/collection/v1/objects/";
    private static final int ATTEMPTS = 4;

    private final CatalogCache cache;

    MetProvider(CatalogCache cache) {
        this.cache = cache;
    }

    @Override public String id() { return "met"; }

    @Override
    public WallpaperItem fetch(LocalDate date, Http http) throws ProviderException {
        JSONArray ids = Json.arr(Json.object(CatalogCache.load(cache, "met_ids", SEARCH_URL, null, http)), "objectIDs");
        int n = ids == null ? 0 : ids.length();
        if (n == 0) throw new ProviderException("no object ids");
        int start = DailySeed.index(date, "met", n);
        ProviderException last = new ProviderException("no object with a picture");
        for (int i = 0; i < ATTEMPTS && i < n; i++) {
            long objectId = ids.optLong((start + i) % n, 0);
            if (objectId <= 0) continue;
            JSONObject o = Json.object(http.getText(OBJECT_URL + objectId, null));
            String image = Json.str(o, "primaryImage");
            if (image.isEmpty() || !o.optBoolean("isPublicDomain", true)) {
                last = new ProviderException("object " + objectId + " has no public picture");
                continue;
            }
            String small = Json.str(o, "primaryImageSmall");
            String artist = Json.clean(Json.str(o, "artistDisplayName"), 100);
            String year = Json.clean(Json.str(o, "objectDate"), 40);
            String credit = artist.isEmpty() ? "The Metropolitan Museum of Art" : artist;
            if (!year.isEmpty()) credit += ", " + year;
            return new WallpaperItem(id(), Json.clean(Json.str(o, "title"), 160), small.isEmpty() ? image : small,
                    image, null, credit, date.toString());
        }
        throw last;
    }
}
