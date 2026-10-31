package com.gathin.tvhome.wallpaper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Collects several pictures of the day, at most one per source. It asks {@link WallpaperAggregator} for the first
 * source that works, hands the picture to a downloader and then goes on with the sources after it, until it has
 * {@code max} downloaded pictures or no source is left. Every source tried leaves one {@link Outcome}. Blocks on
 * the network: call it from a background thread.
 */
public final class DailyCollector {
    private DailyCollector() { }

    /** Saves the picture; returns null on success or a short error. */
    public interface Downloader {
        String download(WallpaperItem item);
    }

    /** What happened to one source. */
    public static final class Outcome {
        public final String provider;
        public final boolean ok;
        public final String message;

        Outcome(String provider, boolean ok, String message) {
            this.provider = provider;
            this.ok = ok;
            this.message = message == null ? "" : message;
        }
    }

    public static final class Collected {
        public final List<WallpaperItem> items;
        public final List<Outcome> outcomes;

        Collected(List<WallpaperItem> items, List<Outcome> outcomes) {
            this.items = Collections.unmodifiableList(items);
            this.outcomes = Collections.unmodifiableList(outcomes);
        }
    }

    /**
     * Sources that show the same pictures (the Bing mirrors, the two Spotlight ones, the three subreddits) share a
     * family: only one picture of a family is collected.
     */
    static String family(String id) {
        if (id.equals("bing") || id.equals("peapix_bing") || id.equals("biturl")) return "bing";
        if (id.equals("spotlight") || id.equals("peapix_spotlight")) return "spotlight";
        if (id.startsWith("reddit_")) return "reddit";
        return id;
    }

    public static Collected collect(WallpaperAggregator aggregator, List<String> order, LocalDate date, int max,
                                    Downloader downloader) {
        List<String> remaining = new ArrayList<>(new LinkedHashSet<>(order));
        remaining.remove(WallpaperAggregator.ALL_RANDOM);
        List<WallpaperItem> items = new ArrayList<>();
        List<Outcome> outcomes = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (items.size() < max && !remaining.isEmpty()) {
            WallpaperAggregator.Result result = aggregator.getDailyWithFallback(remaining, date);
            for (WallpaperAggregator.ProviderError e : result.errors) {
                outcomes.add(new Outcome(e.provider, false, e.message));
            }
            if (!result.ok()) break;
            // The aggregator skips failures in order, so the source that answered follows the failed ones.
            String id = remaining.get(result.errors.size());
            String key = result.item.urlUhd.isEmpty() ? result.item.urlHd : result.item.urlUhd;
            String error = seen.contains(key) ? "same picture as an earlier source" : downloader.download(result.item);
            remaining = new ArrayList<>(remaining.subList(result.errors.size() + 1, remaining.size()));
            if (error == null) {
                seen.add(key);
                items.add(result.item);
                outcomes.add(new Outcome(id, true, result.item.title));
                // The mirrors of this source would only repeat the picture.
                String family = family(id);
                for (java.util.Iterator<String> it = remaining.iterator(); it.hasNext(); ) {
                    if (family(it.next()).equals(family)) it.remove();
                }
            } else {
                outcomes.add(new Outcome(id, false, "download: " + error));
            }
        }
        return new Collected(items, outcomes);
    }
}
