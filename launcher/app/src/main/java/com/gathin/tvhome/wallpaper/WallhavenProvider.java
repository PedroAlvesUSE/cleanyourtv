package com.gathin.tvhome.wallpaper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;

/** Wallhaven top list of the last day, safe for work, general category, 4K or larger. Files over 12 MB are skipped. */
final class WallhavenProvider implements WallpaperProvider {
    static final String URL = "https://wallhaven.cc/api/v1/search?sorting=toplist&topRange=1d&purity=100&categories=100"
            + "&atleast=3840x2160";
    private static final long MAX_FILE_BYTES = 12L * 1024 * 1024;

    @Override public String id() { return "wallhaven"; }

    @Override
    public WallpaperItem fetch(LocalDate date, Http http) throws ProviderException {
        JSONArray data = Json.arr(Json.object(http.getText(URL, null)), "data");
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
