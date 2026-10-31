package com.gathin.tvhome.wallpaper;

import org.json.JSONObject;

import java.time.LocalDate;
import java.util.Map;

/**
 * Art Institute of Chicago, public domain works that have an image. The API and the IIIF image server both refuse
 * a request without an {@code AIC-User-Agent} header (Cloudflare answers 403), so the item carries it for the download.
 * The server serves 843 px and 1686 px wide files; any other width is redirected to 843, so 1686 is the largest.
 */
final class ArticProvider implements WallpaperProvider {
    static final Map<String, String> HEADERS = Json.headers("AIC-User-Agent", UrlConnectionHttp.USER_AGENT);
    /** The API only returns the first 1000 results of a search: page * limit must stay within that. */
    static final int PAGES = 1000;
    private static final String IIIF = "https://www.artic.edu/iiif/2/";

    @Override public String id() { return "artic"; }

    static String url(LocalDate date) {
        int page = DailySeed.index(date, "artic", PAGES) + 1;
        return "https://api.artic.edu/api/v1/artworks/search"
                + "?query%5Bbool%5D%5Bmust%5D%5B0%5D%5Bterm%5D%5Bis_public_domain%5D=true"
                + "&query%5Bbool%5D%5Bmust%5D%5B1%5D%5Bexists%5D%5Bfield%5D=image_id"
                + "&limit=1&page=" + page + "&fields=id,title,image_id,artist_title";
    }

    @Override
    public WallpaperItem fetch(LocalDate date, Http http) throws ProviderException {
        JSONObject artwork = Json.first(Json.arr(Json.object(http.getText(url(date), HEADERS)), "data"));
        String imageId = Json.str(artwork, "image_id");
        if (imageId.isEmpty()) throw new ProviderException("no image_id");
        String uhd = IIIF + imageId + "/full/1686,/0/default.jpg";
        String hd = IIIF + imageId + "/full/843,/0/default.jpg";
        return new WallpaperItem(id(), Json.clean(Json.str(artwork, "title"), 160), hd, uhd, null,
                Json.clean(Json.str(artwork, "artist_title"), 100), date.toString(), HEADERS);
    }
}
