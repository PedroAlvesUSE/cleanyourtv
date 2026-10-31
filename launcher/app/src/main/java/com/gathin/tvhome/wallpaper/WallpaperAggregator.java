package com.gathin.tvhome.wallpaper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picks the day's picture: tries the sources in order and skips the ones that fail (exception, timeout, 429, a NASA
 * video, an empty URL). Every failure is logged and returned, for a diagnostics screen. Blocks on the network: call it
 * from a background thread.
 */
public final class WallpaperAggregator {
    /** Source id that means "every source, in an order shuffled by the day". */
    public static final String ALL_RANDOM = "all_random";

    /** Why one source gave nothing. */
    public static final class ProviderError {
        public final String provider;
        public final String message;

        ProviderError(String provider, String message) {
            this.provider = provider;
            this.message = message;
        }

        @Override
        public String toString() {
            return provider + ": " + message;
        }
    }

    /** The picture (null when every source failed) and the errors of the sources that were tried before it. */
    public static final class Result {
        public final WallpaperItem item;
        public final List<ProviderError> errors;

        Result(WallpaperItem item, List<ProviderError> errors) {
            this.item = item;
            this.errors = Collections.unmodifiableList(errors);
        }

        public boolean ok() {
            return item != null;
        }
    }

    private final Map<String, WallpaperProvider> providers;
    private final Http http;

    /** @param providers by id; the iteration order is the order used by {@code all_random} before it shuffles */
    public WallpaperAggregator(Map<String, WallpaperProvider> providers, Http http) {
        this.providers = providers;
        this.http = http;
    }

    /** One source by id, or {@code all_random}. A single source does not fall back to another one. */
    public Result get(String providerId, LocalDate date) {
        return tryInOrder(Collections.singletonList(providerId), date);
    }

    /**
     * The first source of {@code preferredOrder} that works. An entry may be {@code all_random}. A null or empty
     * list means every source.
     */
    public Result getDailyWithFallback(List<String> preferredOrder, LocalDate date) {
        List<String> order = preferredOrder == null || preferredOrder.isEmpty()
                ? new ArrayList<>(providers.keySet()) : preferredOrder;
        return tryInOrder(order, date);
    }

    private Result tryInOrder(List<String> requested, LocalDate date) {
        Set<String> order = new LinkedHashSet<>();
        for (String id : requested) {
            if (ALL_RANDOM.equals(id)) {
                order.addAll(DailySeed.shuffle(new ArrayList<>(providers.keySet()), date, ALL_RANDOM));
            } else if (id != null) {
                order.add(id);
            }
        }
        List<ProviderError> errors = new ArrayList<>();
        for (String id : order) {
            WallpaperProvider provider = providers.get(id);
            if (provider == null) {
                fail(errors, id, "unknown source");
                continue;
            }
            try {
                WallpaperItem item = provider.fetch(date, http);
                if (item == null || !item.hasImage()) {
                    fail(errors, id, "empty image URL");
                    continue;
                }
                return new Result(item, errors);
            } catch (ProviderException e) {
                fail(errors, id, e.getMessage());
            } catch (RuntimeException e) {
                fail(errors, id, "unexpected " + e);
            }
        }
        return new Result(null, errors);
    }

    private static void fail(List<ProviderError> errors, String id, String message) {
        String text = message == null ? "failed" : message;
        errors.add(new ProviderError(id, text));
        WallpaperLog.warn(id + " skipped: " + text);
    }
}
