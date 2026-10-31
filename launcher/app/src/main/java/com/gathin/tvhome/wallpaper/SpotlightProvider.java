package com.gathin.tvhome.wallpaper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;

/** Windows Spotlight. The answer holds a string with a second JSON document inside (batchrsp.items[0].item). */
final class SpotlightProvider implements WallpaperProvider {
    static final String URL = "https://arc.msn.com/v3/Delivery/Placement?pid=209567&fmt=json&rafb=0&ua=WindowsShellClient%2F0"
            + "&cdm=1&disphorzres=3840&dispvertres=2160&lo=80217&pl=pt-BR&lc=pt-BR&ctry=br";

    @Override public String id() { return "spotlight"; }

    @Override
    public WallpaperItem fetch(LocalDate date, Http http) throws ProviderException {
        JSONArray items = Json.arr(Json.obj(Json.object(http.getText(URL, null)), "batchrsp"), "items");
        JSONObject first = Json.first(items);
        String nested = Json.str(first, "item");
        if (nested.isEmpty()) throw new ProviderException("no items");
        JSONObject ad = Json.obj(Json.object(nested), "ad");
        String landscape = Json.str(Json.obj(ad, "image_fullscreen_001_landscape"), "u");
        if (landscape.isEmpty()) throw new ProviderException("no landscape image");
        String portrait = Json.str(Json.obj(ad, "image_fullscreen_001_portrait"), "u");
        String title = Json.str(Json.obj(ad, "title_text"), "tx");
        String copyright = Json.str(Json.obj(ad, "copyright_text"), "tx");
        // Spotlight serves one landscape size (1920x1080 when the request asks for 4K): both fields get it.
        return new WallpaperItem(id(), title, landscape, landscape, portrait, copyright, date.toString());
    }
}
