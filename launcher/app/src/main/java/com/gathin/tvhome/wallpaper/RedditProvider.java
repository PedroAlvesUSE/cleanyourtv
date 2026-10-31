package com.gathin.tvhome.wallpaper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.Map;

/**
 * Top post of the day of a wallpaper subreddit: an image post, not NSFW, landscape. When the preview source is 3000 px
 * wide or more it becomes the UHD URL (its &amp; escapes are undone); the post's own image is the HD URL.
 * Reddit asks for a descriptive User-Agent and often answers 403 to unknown clients; the aggregator then moves on.
 */
final class RedditProvider implements WallpaperProvider {
    static final Map<String, String> HEADERS = Json.headers(
            "User-Agent", "android:com.gathin.tvhome:v2.4 (by /u/cleanyourtv)");
    private static final int UHD_MIN_WIDTH = 3000;

    private final String sub;
    private final String id;

    private RedditProvider(String id, String sub) {
        this.id = id;
        this.sub = sub;
    }

    static RedditProvider earthPorn() { return new RedditProvider("reddit_earthporn", "EarthPorn"); }

    static RedditProvider wallpapers() { return new RedditProvider("reddit_wallpapers", "wallpapers"); }

    static RedditProvider amoledBackgrounds() { return new RedditProvider("reddit_amoledbackgrounds", "AmoledBackgrounds"); }

    @Override public String id() { return id; }

    String url() {
        return "https://www.reddit.com/r/" + sub + "/top.json?t=day&limit=10&raw_json=1";
    }

    @Override
    public WallpaperItem fetch(LocalDate date, Http http) throws ProviderException {
        JSONArray children = Json.arr(Json.obj(Json.object(http.getText(url(), HEADERS)), "data"), "children");
        if (children == null) throw new ProviderException("no posts");
        for (int i = 0; i < children.length(); i++) {
            JSONObject post = Json.obj(children.optJSONObject(i), "data");
            if (post == null || !"image".equals(Json.str(post, "post_hint")) || post.optBoolean("over_18", false)) continue;
            String image = unescape(Json.str(post, "url_overridden_by_dest"));
            if (!image.startsWith("http")) continue;
            JSONObject source = Json.first(Json.arr(Json.obj(post, "preview"), "images"));
            source = Json.obj(source, "source");
            int width = source == null ? 0 : source.optInt("width", 0);
            int height = source == null ? 0 : source.optInt("height", 0);
            if (width > 0 && height > width) continue;   // a phone picture
            String preview = unescape(Json.str(source, "url"));
            String uhd = width >= UHD_MIN_WIDTH && preview.startsWith("http") ? preview : image;
            String author = Json.str(post, "author");
            return new WallpaperItem(id, Json.clean(unescape(Json.str(post, "title")), 160), image, uhd, null,
                    (author.isEmpty() ? "" : "u/" + author + " · ") + "r/" + sub, date.toString());
        }
        throw new ProviderException("no suitable image post in r/" + sub);
    }

    private static String unescape(String text) {
        return text.replace("&amp;", "&");
    }
}
