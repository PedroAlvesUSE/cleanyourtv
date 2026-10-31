package com.gathin.tvhome.wallpaper;

import org.json.JSONObject;

import java.time.LocalDate;

/** bing.biturl.top, a JSON front for the Bing picture of the day. */
final class BiturlProvider implements WallpaperProvider {
    static final String URL = "https://bing.biturl.top/?resolution=UHD&format=json&index=0&mkt=pt-BR";

    @Override public String id() { return "biturl"; }

    @Override
    public WallpaperItem fetch(LocalDate date, Http http) throws ProviderException {
        JSONObject o = Json.object(http.getText(URL, null));
        String uhd = Json.str(o, "url");
        if (uhd.isEmpty()) throw new ProviderException("no url");
        String hd = uhd.endsWith("_UHD.jpg") ? uhd.substring(0, uhd.length() - 8) + "_1920x1080.jpg" : uhd;
        String copyright = Json.str(o, "copyright");
        int credit = copyright.indexOf(" (©");
        String title = credit > 0 ? copyright.substring(0, credit) : copyright;
        return new WallpaperItem(id(), title, hd, uhd, null, copyright, date.toString());
    }
}
