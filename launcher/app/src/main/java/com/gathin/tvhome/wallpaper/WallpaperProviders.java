package com.gathin.tvhome.wallpaper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All the sources by id, and the order the launcher tries them in by default. */
public final class WallpaperProviders {
    private WallpaperProviders() { }

    /** Every id, in registry order. The two key-only sources come last. */
    public static final List<String> ALL_IDS = Collections.unmodifiableList(Arrays.asList(
            "bing", "peapix_bing", "biturl", "spotlight", "peapix_spotlight", "chromecast", "earthview",
            "wikimedia", "nasa", "wallhaven", "reddit_earthporn", "reddit_wallpapers", "reddit_amoledbackgrounds",
            "artic", "met", "picsum", "unsplash", "pexels"));

    /** Suggested order. picsum is last because it never fails; unsplash and pexels need a key and are not here. */
    public static final List<String> DEFAULT_ORDER = Collections.unmodifiableList(Arrays.asList(
            "bing", "peapix_bing", "biturl", "spotlight", "chromecast", "earthview", "wikimedia", "nasa",
            "wallhaven", "reddit_earthporn", "artic", "met", "picsum"));

    /**
     * @param cache       for the static catalogs; may be null
     * @param nasaKey     NASA api key; null or empty means DEMO_KEY
     * @param unsplashKey Unsplash access key; null or empty means that source always fails with "no key"
     * @param pexelsKey   Pexels api key; same
     */
    public static Map<String, WallpaperProvider> create(CatalogCache cache, String nasaKey, String unsplashKey,
                                                        String pexelsKey) {
        return create(cache, nasaKey, unsplashKey, pexelsKey, null);
    }

    /** As above; a non-empty {@code wallhavenQuery} makes Wallhaven search that holiday term. */
    public static Map<String, WallpaperProvider> create(CatalogCache cache, String nasaKey, String unsplashKey,
                                                        String pexelsKey, String wallhavenQuery) {
        List<WallpaperProvider> list = new ArrayList<>();
        list.add(new BingProvider());
        list.add(PeapixProvider.bing());
        list.add(new BiturlProvider());
        list.add(new SpotlightProvider());
        list.add(PeapixProvider.spotlight());
        list.add(new ChromecastProvider(cache));
        list.add(new EarthViewProvider(cache));
        list.add(new WikimediaProvider());
        list.add(new NasaApodProvider(nasaKey));
        list.add(new WallhavenProvider(wallhavenQuery));
        list.add(RedditProvider.earthPorn());
        list.add(RedditProvider.wallpapers());
        list.add(RedditProvider.amoledBackgrounds());
        list.add(new ArticProvider());
        list.add(new MetProvider(cache));
        list.add(new PicsumProvider());
        list.add(new UnsplashProvider(unsplashKey));
        list.add(new PexelsProvider(pexelsKey));
        Map<String, WallpaperProvider> map = new LinkedHashMap<>();
        for (WallpaperProvider provider : list) map.put(provider.id(), provider);
        return map;
    }

    /** An aggregator on the real network; the catalogs are cached in {@code catalogDir} (may be null). */
    public static WallpaperAggregator newAggregator(File catalogDir, String nasaKey, String unsplashKey,
                                                    String pexelsKey) {
        return newAggregator(catalogDir, nasaKey, unsplashKey, pexelsKey, null);
    }

    public static WallpaperAggregator newAggregator(File catalogDir, String nasaKey, String unsplashKey,
                                                    String pexelsKey, String wallhavenQuery) {
        CatalogCache cache = catalogDir == null ? null : new CatalogCache(catalogDir);
        return new WallpaperAggregator(create(cache, nasaKey, unsplashKey, pexelsKey, wallhavenQuery),
                new UrlConnectionHttp());
    }
}
