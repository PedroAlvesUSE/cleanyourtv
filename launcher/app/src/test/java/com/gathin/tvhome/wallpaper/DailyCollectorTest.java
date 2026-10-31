package com.gathin.tvhome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DailyCollectorTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 30);

    private static WallpaperProvider fake(String id, boolean works) {
        return new WallpaperProvider() {
            @Override public String id() { return id; }

            @Override public WallpaperItem fetch(LocalDate date, Http http) throws ProviderException {
                if (!works) throw new ProviderException("down");
                return new WallpaperItem(id, id + " title", "https://i/" + id + "-hd.jpg", "https://i/" + id + ".jpg",
                        null, "credit", date.toString());
            }
        };
    }

    private static WallpaperAggregator aggregator(WallpaperProvider... providers) {
        Map<String, WallpaperProvider> map = new LinkedHashMap<>();
        for (WallpaperProvider p : providers) map.put(p.id(), p);
        return new WallpaperAggregator(map, new FakeHttp());
    }

    @Test
    public void collectsOnePictureFromEachSourceInOrderSkippingFailures() {
        WallpaperAggregator agg = aggregator(fake("a", true), fake("b", false), fake("c", true), fake("d", true));
        DailyCollector.Collected got = DailyCollector.collect(agg, Arrays.asList("a", "b", "c", "d"), DAY, 6,
                item -> null);
        assertEquals(3, got.items.size());
        assertEquals("a", got.items.get(0).provider);
        assertEquals("c", got.items.get(1).provider);
        assertEquals("d", got.items.get(2).provider);
        assertEquals(4, got.outcomes.size());
        assertEquals("b", got.outcomes.get(1).provider);
        assertFalse(got.outcomes.get(1).ok);
    }

    @Test
    public void stopsAtTheMaximumWithoutAskingTheOtherSources() {
        List<String> asked = new ArrayList<>();
        WallpaperProvider a = fake("a", true);
        WallpaperProvider b = fake("b", true);
        WallpaperProvider c = new WallpaperProvider() {
            @Override public String id() { return "c"; }

            @Override public WallpaperItem fetch(LocalDate date, Http http) {
                asked.add("c");
                return null;
            }
        };
        DailyCollector.Collected got = DailyCollector.collect(aggregator(a, b, c), Arrays.asList("a", "b", "c"), DAY,
                2, item -> null);
        assertEquals(2, got.items.size());
        assertTrue(asked.isEmpty());
    }

    @Test
    public void aFailedDownloadMovesOnToTheNextSource() {
        WallpaperAggregator agg = aggregator(fake("a", true), fake("b", true));
        DailyCollector.Collected got = DailyCollector.collect(agg, Arrays.asList("a", "b"), DAY, 6,
                item -> item.provider.equals("a") ? "too large" : null);
        assertEquals(1, got.items.size());
        assertEquals("b", got.items.get(0).provider);
        assertEquals("download: too large", got.outcomes.get(0).message);
        assertFalse(got.outcomes.get(0).ok);
        assertTrue(got.outcomes.get(1).ok);
    }

    @Test
    public void everySourceFailingGivesNoPictureAndOneOutcomeEach() {
        DailyCollector.Collected got = DailyCollector.collect(aggregator(fake("a", false), fake("b", false)),
                Arrays.asList("a", "b", "missing"), DAY, 6, item -> null);
        assertTrue(got.items.isEmpty());
        assertEquals(3, got.outcomes.size());
        assertEquals("unknown source", got.outcomes.get(2).message);
    }

    @Test
    public void mirrorsOfAnAnsweringSourceAreNotAskedAgain() {
        WallpaperAggregator agg = aggregator(fake("bing", true), fake("peapix_bing", true), fake("biturl", true),
                fake("met", true));
        DailyCollector.Collected got = DailyCollector.collect(agg,
                Arrays.asList("bing", "peapix_bing", "biturl", "met"), DAY, 6, item -> null);
        assertEquals(2, got.items.size());
        assertEquals("bing", got.items.get(0).provider);
        assertEquals("met", got.items.get(1).provider);
    }

    @Test
    public void aMirrorTakesOverWhenItsFamilyMemberFailsToDownload() {
        WallpaperAggregator agg = aggregator(fake("bing", true), fake("peapix_bing", true));
        DailyCollector.Collected got = DailyCollector.collect(agg, Arrays.asList("bing", "peapix_bing"), DAY, 6,
                item -> item.provider.equals("bing") ? "http 500" : null);
        assertEquals(1, got.items.size());
        assertEquals("peapix_bing", got.items.get(0).provider);
    }

    @Test
    public void theSameUrlIsNotCollectedTwice() {
        WallpaperProvider twin = new WallpaperProvider() {
            @Override public String id() { return "twin"; }

            @Override public WallpaperItem fetch(LocalDate date, Http http) {
                return new WallpaperItem("twin", "t", "https://i/a-hd.jpg", "https://i/a.jpg", null, "", date.toString());
            }
        };
        DailyCollector.Collected got = DailyCollector.collect(aggregator(fake("a", true), twin),
                Arrays.asList("a", "twin"), DAY, 6, item -> null);
        assertEquals(1, got.items.size());
        assertFalse(got.outcomes.get(1).ok);
    }
}
