package com.gathin.tvhome.wallpaper;

import org.json.JSONObject;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Wikipedia "featured picture" of the day. The original file can be 8000 px and tens of MB, so the URLs ask the
 * Wikimedia thumbnail service for 3840 and 1920 px instead. The API needs a User-Agent (UrlConnectionHttp sends one).
 */
final class WikimediaProvider implements WallpaperProvider {
    private static final String BASE = "https://api.wikimedia.org/feed/v1/wikipedia/en/featured/";
    private static final Pattern THUMB_WIDTH = Pattern.compile("/\\d+px-");

    @Override public String id() { return "wikimedia"; }

    static String url(LocalDate date) {
        return BASE + String.format(Locale.ROOT, "%04d/%02d/%02d", date.getYear(), date.getMonthValue(),
                date.getDayOfMonth());
    }

    @Override
    public WallpaperItem fetch(LocalDate date, Http http) throws ProviderException {
        JSONObject image = Json.obj(Json.object(http.getText(url(date), null)), "image");
        JSONObject original = Json.obj(image, "image");
        String source = Json.str(original, "source");
        if (source.isEmpty()) throw new ProviderException("no featured image today");
        String path = source.contains("?") ? source.substring(0, source.indexOf('?')) : source;
        String ext = path.toLowerCase(Locale.ROOT);
        if (!(ext.endsWith(".jpg") || ext.endsWith(".jpeg") || ext.endsWith(".png") || ext.endsWith(".webp"))) {
            throw new ProviderException("not a usable image: " + path);
        }
        int width = original.optInt("width", 0);
        String thumb = Json.str(Json.obj(image, "thumbnail"), "source");
        String hd = source;
        String uhd = source;
        if (width > 1920 && thumb.contains("/thumb/") && THUMB_WIDTH.matcher(thumb).find()) {
            hd = THUMB_WIDTH.matcher(thumb).replaceFirst("/1920px-");
            uhd = width > 3840 ? THUMB_WIDTH.matcher(thumb).replaceFirst("/3840px-") : source;
        }
        String title = Json.clean(Json.str(Json.obj(image, "description"), "text"), 160);
        if (title.isEmpty()) title = Json.clean(Json.str(image, "title").replaceFirst("^File:", ""), 160);
        String artist = Json.clean(Json.str(Json.obj(image, "artist"), "text"), 100);
        return new WallpaperItem(id(), title, hd, uhd, null, artist.isEmpty() ? "Wikimedia Commons" : artist,
                date.toString());
    }
}
