package com.gathin.tvhome.wallpaper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chromecast backdrops: a static list, one entry a day chosen by {@link DailySeed}. */
final class ChromecastProvider implements WallpaperProvider {
    static final String URL = "https://raw.githubusercontent.com/dconnolly/chromecast-backgrounds/master/backgrounds.json";

    // googleusercontent size tails: ".../s1920-w1920-h1080-c/name.jpg", "...=s1920" or "...=w1920-h1080-c"
    private static final Pattern PATH_SIZE = Pattern.compile("/s\\d+-w\\d+-h\\d+(-[a-z]+)*/");
    private static final Pattern SUFFIX_SIZE = Pattern.compile("=(s\\d+|w\\d+-h\\d+)(-[a-z]+)*$");

    private final CatalogCache cache;

    ChromecastProvider(CatalogCache cache) {
        this.cache = cache;
    }

    @Override public String id() { return "chromecast"; }

    @Override
    public WallpaperItem fetch(LocalDate date, Http http) throws ProviderException {
        JSONArray list = Json.array(CatalogCache.load(cache, "chromecast", URL, null, http));
        int n = list.length();
        if (n == 0) throw new ProviderException("empty catalog");
        int start = DailySeed.index(date, "chromecast", n);
        for (int i = 0; i < n; i++) {
            JSONObject entry = list.optJSONObject((start + i) % n);
            String url = Json.str(entry, "url");
            if (!url.startsWith("http")) continue;
            String author = Json.str(entry, "author");
            return new WallpaperItem(id(), "Chromecast", resize(url, 1920, 1080), resize(url, 3840, 2160), null,
                    author.isEmpty() ? "Google Chromecast" : "© " + author, date.toString());
        }
        throw new ProviderException("no usable entry");
    }

    /** Rewrites a googleusercontent size tail; other hosts and unknown shapes keep the URL as it is. */
    static String resize(String url, int width, int height) {
        if (!url.contains("googleusercontent.com")) return url;
        Matcher path = PATH_SIZE.matcher(url);
        if (path.find()) {
            return url.substring(0, path.start()) + "/s" + width + "-w" + width + "-h" + height + "-c/"
                    + url.substring(path.end());
        }
        Matcher suffix = SUFFIX_SIZE.matcher(url);
        if (suffix.find()) {
            return url.substring(0, suffix.start()) + "=w" + width + "-h" + height + "-c";
        }
        return url;
    }
}
