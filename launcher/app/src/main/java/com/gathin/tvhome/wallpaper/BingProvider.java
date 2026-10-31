package com.gathin.tvhome.wallpaper;

import org.json.JSONObject;

import java.time.LocalDate;

/** Bing picture of the day. urlbase + "_UHD.jpg" is the 3840x2160 file. */
final class BingProvider implements WallpaperProvider {
    static final String URL = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=pt-BR";
    private static final String HOST = "https://www.bing.com";

    @Override public String id() { return "bing"; }

    @Override
    public WallpaperItem fetch(LocalDate date, Http http) throws ProviderException {
        JSONObject image = Json.first(Json.arr(Json.object(http.getText(URL, null)), "images"));
        String base = Json.str(image, "urlbase");
        if (base.isEmpty()) throw new ProviderException("no urlbase");
        String base1 = base.startsWith("http") ? base : HOST + base;
        return new WallpaperItem(id(), Json.str(image, "title"), base1 + "_1920x1080.jpg", base1 + "_UHD.jpg",
                null, Json.str(image, "copyright"), date.toString());
    }
}
