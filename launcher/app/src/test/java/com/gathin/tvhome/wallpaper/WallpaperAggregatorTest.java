package com.gathin.tvhome.wallpaper;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WallpaperAggregatorTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 30);

    /** A source that gives a fixed picture, or throws. */
    private static final class Fake implements WallpaperProvider {
        final String id;
        final String url;
        final ProviderException error;
        int calls;

        Fake(String id, String url, ProviderException error) {
            this.id = id;
            this.url = url;
            this.error = error;
        }

        @Override public String id() { return id; }

        @Override public WallpaperItem fetch(LocalDate date, Http http) throws ProviderException {
            calls++;
            if (error != null) throw error;
            return new WallpaperItem(id, id + " title", url, url, null, "", date.toString());
        }
    }

    private static Fake ok(String id) { return new Fake(id, "https://img.test/" + id + ".jpg", null); }

    private static Fake failing(String id, String why) { return new Fake(id, null, new ProviderException(why)); }

    private static WallpaperAggregator aggregator(WallpaperProvider... providers) {
        Map<String, WallpaperProvider> map = new LinkedHashMap<>();
        for (WallpaperProvider p : providers) map.put(p.id(), p);
        return new WallpaperAggregator(map, new FakeHttp());
    }

    @Test public void firstThrowsSecondAnswers() {
        Fake first = failing("a", "timeout");
        Fake second = ok("b");
        WallpaperAggregator.Result result = aggregator(first, second, ok("c"))
                .getDailyWithFallback(Arrays.asList("a", "b", "c"), DAY);
        assertTrue(result.ok());
        assertEquals("b", result.item.provider);
        assertEquals(1, result.errors.size());
        assertEquals("a", result.errors.get(0).provider);
        assertEquals("timeout", result.errors.get(0).message);
    }

    @Test public void sourcesAfterTheWinnerAreNotCalled() {
        Fake third = ok("c");
        aggregator(ok("a"), ok("b"), third).getDailyWithFallback(Arrays.asList("a", "b", "c"), DAY);
        assertEquals(0, third.calls);
    }

    @Test public void orderOfThePreferenceIsKept() {
        WallpaperAggregator.Result result = aggregator(ok("a"), ok("b")).getDailyWithFallback(Arrays.asList("b", "a"), DAY);
        assertEquals("b", result.item.provider);
    }

    @Test public void rateLimitAndEmptyUrlAreSkipped() {
        WallpaperAggregator.Result result = aggregator(failing("a", "HTTP 429 (rate limited)"), new Fake("b", "", null), ok("c"))
                .getDailyWithFallback(Arrays.asList("a", "b", "c"), DAY);
        assertEquals("c", result.item.provider);
        assertEquals(2, result.errors.size());
        assertEquals("empty image URL", result.errors.get(1).message);
    }

    @Test public void unknownIdIsReportedAndSkipped() {
        WallpaperAggregator.Result result = aggregator(ok("a")).getDailyWithFallback(Arrays.asList("nope", "a"), DAY);
        assertEquals("a", result.item.provider);
        assertEquals("unknown source", result.errors.get(0).message);
    }

    @Test public void unexpectedRuntimeErrorIsSkippedToo() {
        WallpaperProvider broken = new WallpaperProvider() {
            @Override public String id() { return "broken"; }
            @Override public WallpaperItem fetch(LocalDate date, Http http) { throw new IllegalStateException("boom"); }
        };
        WallpaperAggregator.Result result = aggregator(broken, ok("b")).getDailyWithFallback(Arrays.asList("broken", "b"), DAY);
        assertEquals("b", result.item.provider);
        assertTrue(result.errors.get(0).message.contains("boom"));
    }

    @Test public void everySourceFailingGivesNoItemAndAllErrors() {
        WallpaperAggregator.Result result = aggregator(failing("a", "x"), failing("b", "y"))
                .getDailyWithFallback(Arrays.asList("a", "b"), DAY);
        assertFalse(result.ok());
        assertNull(result.item);
        assertEquals(2, result.errors.size());
    }

    @Test public void nullOrEmptyPreferenceTriesEverySource() {
        assertEquals("a", aggregator(ok("a"), ok("b")).getDailyWithFallback(null, DAY).item.provider);
        assertEquals("a", aggregator(ok("a"), ok("b")).getDailyWithFallback(new ArrayList<String>(), DAY).item.provider);
    }

    @Test public void singleSourceDoesNotFallBack() {
        WallpaperAggregator.Result result = aggregator(failing("a", "x"), ok("b")).get("a", DAY);
        assertFalse(result.ok());
        assertEquals(1, result.errors.size());
    }

    @Test public void nasaVideoIsSkipped() {
        String video = FakeHttp.sample("nasa_video");
        FakeHttp http = new FakeHttp().on("https://api.nasa.gov/", video);
        Map<String, WallpaperProvider> map = new LinkedHashMap<>();
        map.put("nasa", new NasaApodProvider(null));
        map.put("picsum", new PicsumProvider());
        WallpaperAggregator.Result result = new WallpaperAggregator(map, http)
                .getDailyWithFallback(Arrays.asList("nasa", "picsum"), DAY);
        assertEquals("picsum", result.item.provider);
        assertEquals("nasa", result.errors.get(0).provider);
        assertTrue(result.errors.get(0).message.contains("video"));
    }

    @Test public void allRandomIsTheSameAllDayAndCoversEverySource() {
        List<String> ids = Arrays.asList("a", "b", "c", "d", "e", "f");
        WallpaperProvider[] all = new WallpaperProvider[ids.size()];
        for (int i = 0; i < all.length; i++) all[i] = ok(ids.get(i));
        String one = aggregator(all).get(WallpaperAggregator.ALL_RANDOM, DAY).item.provider;
        for (int i = 0; i < 5; i++) {
            assertEquals(one, aggregator(all).get(WallpaperAggregator.ALL_RANDOM, DAY).item.provider);
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int d = 0; d < 40; d++) seen.add(aggregator(all).get("all_random", DAY.plusDays(d)).item.provider);
        assertTrue("all_random picked only " + seen, seen.size() >= 4);
    }

    @Test public void allRandomFallsThroughToTheNextSourceOfTheDay() {
        WallpaperAggregator.Result result = aggregator(failing("a", "x"), failing("b", "y"), ok("c")).get("all_random", DAY);
        assertNotNull(result.item);
        assertEquals("c", result.item.provider);
    }

    @Test public void allRandomInsideAPreferenceListIsExpandedOnce() {
        WallpaperAggregator.Result result = aggregator(failing("a", "x"), ok("b"))
                .getDailyWithFallback(Arrays.asList("a", "all_random"), DAY);
        assertEquals("b", result.item.provider);
        assertEquals(1, result.errors.size());   // "a" is not tried twice
    }

    @Test public void defaultRegistryHasEveryIdAndPicsumNeverFails() {
        Map<String, WallpaperProvider> all = WallpaperProviders.create(null, null, null, null);
        assertEquals(WallpaperProviders.ALL_IDS, new ArrayList<>(all.keySet()));
        assertTrue(all.keySet().containsAll(WallpaperProviders.DEFAULT_ORDER));
        assertEquals("picsum", WallpaperProviders.DEFAULT_ORDER.get(WallpaperProviders.DEFAULT_ORDER.size() - 1));
        WallpaperAggregator.Result result = new WallpaperAggregator(all, new FakeHttp())   // no network at all
                .getDailyWithFallback(WallpaperProviders.DEFAULT_ORDER, DAY);
        assertEquals("picsum", result.item.provider);
        assertEquals(WallpaperProviders.DEFAULT_ORDER.size() - 1, result.errors.size());
    }
}
