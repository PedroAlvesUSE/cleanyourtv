package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhome.wallpaper.WallpaperProviders;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WallpaperSourcesTest {
    private static final WallpaperSources.Keys NO_KEYS = new WallpaperSources.Keys("", "", "");
    private static final WallpaperSources.Keys KEYS = new WallpaperSources.Keys("n", "u", "p");

    @Test
    public void defaultOrderStartsWithTheSuggestedOnesAndListsEverySource() {
        List<String> order = WallpaperSources.order(null);
        assertEquals(WallpaperProviders.ALL_IDS.size(), order.size());
        assertTrue(order.containsAll(WallpaperProviders.ALL_IDS));
        assertEquals(WallpaperProviders.DEFAULT_ORDER, order.subList(0, WallpaperProviders.DEFAULT_ORDER.size()));
    }

    @Test
    public void storedOrderWinsUnknownIdsAreDroppedMissingOnesAreAppended() {
        List<String> order = WallpaperSources.order("met, nope, bing,met");
        assertEquals("met", order.get(0));
        assertEquals("bing", order.get(1));
        assertEquals(WallpaperProviders.ALL_IDS.size(), order.size());
    }

    @Test
    public void orderSurvivesAStoreAndLoad() {
        List<String> order = WallpaperSources.order(null);
        assertTrue(WallpaperSources.move(order, 5, -5));
        assertTrue(WallpaperSources.move(order, 0, 2));
        assertEquals(order, WallpaperSources.order(WallpaperSources.joinOrder(order)));
    }

    @Test
    public void moveStopsAtTheEnds() {
        List<String> order = new ArrayList<>(Arrays.asList("a", "b", "c"));
        assertFalse(WallpaperSources.move(order, 0, -1));
        assertFalse(WallpaperSources.move(order, 2, 1));
        assertTrue(WallpaperSources.move(order, 0, 1));
        assertEquals(Arrays.asList("b", "a", "c"), order);
    }

    @Test
    public void defaultsOnForTheSuggestedSourcesOffForTheOthers() {
        assertTrue(WallpaperSources.enabled("bing", null, NO_KEYS));
        assertTrue(WallpaperSources.enabled("picsum", null, NO_KEYS));
        assertFalse(WallpaperSources.enabled("reddit_wallpapers", null, NO_KEYS));
        assertFalse(WallpaperSources.enabled("peapix_spotlight", null, NO_KEYS));
    }

    @Test
    public void keyOnlySourcesAreUnavailableWithoutAKeyAndOnWithOne() {
        assertTrue(WallpaperSources.unavailable("unsplash", NO_KEYS));
        assertTrue(WallpaperSources.unavailable("pexels", NO_KEYS));
        assertFalse(WallpaperSources.unavailable("unsplash", KEYS));
        assertFalse(WallpaperSources.enabled("unsplash", "unsplash:1", NO_KEYS));
        assertTrue(WallpaperSources.enabled("unsplash", null, KEYS));
        assertFalse(WallpaperSources.enabled("pexels", "pexels:0", KEYS));
        assertEquals("unsplash_key", WallpaperSources.keyName("unsplash"));
    }

    @Test
    public void explicitStateBeatsTheDefault() {
        String states = WallpaperSources.withState(null, "bing", false);
        states = WallpaperSources.withState(states, "reddit_wallpapers", true);
        assertFalse(WallpaperSources.enabled("bing", states, NO_KEYS));
        assertTrue(WallpaperSources.enabled("reddit_wallpapers", states, NO_KEYS));
        assertTrue(WallpaperSources.enabled("met", states, NO_KEYS));
        assertEquals("bing:0,reddit_wallpapers:1", states);
    }

    @Test
    public void activeFollowsTheUsersOrderAndSkipsDisabledSources() {
        String order = WallpaperSources.joinOrder(Arrays.asList("met", "bing", "nasa"));
        List<String> active = WallpaperSources.active(order, "bing:0", NO_KEYS);
        assertEquals("met", active.get(0));
        assertEquals("nasa", active.get(1));
        assertFalse(active.contains("bing"));
        assertFalse(active.contains("unsplash"));
        assertTrue(WallpaperSources.active(null, null, KEYS).contains("unsplash"));
    }

    @Test
    public void seasonalMovesWallhavenFirstOnlyWhenItIsActive() {
        List<String> active = Arrays.asList("bing", "met", "wallhaven");
        assertEquals(Arrays.asList("wallhaven", "bing", "met"), WallpaperSources.seasonal(active, "christmas"));
        assertEquals(active, WallpaperSources.seasonal(active, null));
        assertEquals(Arrays.asList("bing", "met"), WallpaperSources.seasonal(Arrays.asList("bing", "met"), "christmas"));
    }

    @Test
    public void everySourceHasAName() {
        for (String id : WallpaperProviders.ALL_IDS) {
            assertFalse(id, WallpaperSources.name(id).equals(id.toUpperCase()));
        }
    }

    @Test
    public void diagnosticsKeepTheRowsOfTheLastSearch() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"bing", "1", "Mountain\tlake"});
        rows.add(new String[]{"reddit_earthporn", "0", "HTTP 429 (rate limited)"});
        List<String[]> back = WallpaperSources.parseDiag(WallpaperSources.formatDiag(rows));
        assertEquals(2, back.size());
        assertEquals("bing", back.get(0)[0]);
        assertEquals("1", back.get(0)[1]);
        assertEquals("Mountain lake", back.get(0)[2]);
        assertEquals("0", back.get(1)[1]);
        assertEquals("HTTP 429 (rate limited)", back.get(1)[2]);
        assertTrue(WallpaperSources.parseDiag(null).isEmpty());
    }
}
