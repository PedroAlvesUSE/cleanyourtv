package com.gathin.tvhome.wallpaper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;

/** Wallhaven top list of the last day, safe for work, general category, 4K or larger. Files over 12 MB are skipped. */
final class WallhavenProvider implements WallpaperProvider {
    static final String URL = "https://wallhaven.cc/api/v1/search?sorting=toplist&topRange=1d&purity=100&categories=100"
            + "&atleast=3840x2160";
    private static final long MAX_FILE_BYTES = 12L * 1024 * 1024;

    private final String query;

    WallhavenProvider() {
        this(null);
    }

    /** @param query search term for a holiday (for example {@code christmas}); null or empty is the normal top list */
    WallhavenProvider(String query) {
        this.query = query == null ? "" : query.trim();
    }

    /** A random but stable-for-the-day list of the term, safe for work, 4K or larger. */
    static String seasonalUrl(String query, LocalDate date) {
        String seed = String.format(java.util.Locale.US, "%02d%02d%02d", date.getYear() % 100,
                date.getMonthValue(), date.getDayOfMonth());
        return "https://wallhaven.cc/api/v1/search?q=" + Json.encode(query) + "&sorting=random&seed=" + seed
                + "&purity=100&categories=111&atleast=3840x2160";
    }

    @Override public String id() { return "wallhaven"; }

    @Override
    public WallpaperItem fetch(LocalDate date, Http http) throws ProviderException {
        String url = query.isEmpty() ? URL : seasonalUrl(query, date);
        JSONArray data = Json.arr(Json.object(http.getText(url, null)), "data");
        if (data == null || data.length() == 0) throw new ProviderException("empty top list");
        for (int i = 0; i < data.length(); i++) {
            JSONObject wall = data.optJSONObject(i);
            String path = Json.str(wall, "path");
            if (!path.startsWith("http")) continue;
            long size = wall.optLong("file_size", 0);
            if (size > MAX_FILE_BYTES) continue;
            String page = Json.str(wall, "url");
            return new WallpaperItem(id(), "Wallhaven " + Json.str(wall, "id"), path, path, null,
                    page.isEmpty() ? "wallhaven.cc" : page, date.toString());
        }
        throw new ProviderException("no picture under 12 MB in the top list");
    }
}
