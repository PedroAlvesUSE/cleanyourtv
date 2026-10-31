package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhome.ContinueStore.Item;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ContinueStoreTest {
    private static final long NOW = 1_800_000_000_000L;

    private static Item item(String pkg, String id, long at) {
        return new Item(pkg, id, "Title " + id, null, null, null, null, 0.5f, 0, at, ContinueStore.SOURCE_PROVIDER);
    }

    private static Item media(String pkg, String id, long at) {
        return new Item(pkg, id, "Media " + id, null, null, null, null, 0.5f, 0, at, ContinueStore.SOURCE_MEDIA);
    }

    @Test
    public void upsertReplacesSameKey() {
        List<Item> list = new ArrayList<>();
        assertTrue(ContinueStore.upsert(list, item("a", "1", NOW - 10), NOW));
        assertTrue(ContinueStore.upsert(list, item("b", "1", NOW - 5), NOW));
        Item newer = new Item("a", "1", "New", null, null, null, null, 0.7f, 0, NOW, ContinueStore.SOURCE_PROVIDER);
        assertTrue(ContinueStore.upsert(list, newer, NOW));
        assertEquals(2, list.size());
        assertEquals("New", list.get(0).title);
        assertEquals("b", list.get(1).pkg);
    }

    @Test
    public void limitPerAppKeepsNewest() {
        List<Item> list = new ArrayList<>();
        for (int i = 0; i < ContinueStore.MAX_PER_APP + 5; i++) ContinueStore.upsert(list, item("a", "i" + i, NOW - 1000 + i), NOW);
        ContinueStore.upsert(list, item("b", "x", NOW - 2000), NOW);
        int a = 0;
        for (Item it : list) if (it.pkg.equals("a")) a++;
        assertEquals(ContinueStore.MAX_PER_APP, a);
        for (Item it : list) assertFalse(it.id.equals("i0") || it.id.equals("i4"));
        assertEquals(ContinueStore.MAX_PER_APP + 1, list.size());
    }

    @Test
    public void expiredItemsGo() {
        List<Item> list = new ArrayList<>();
        list.add(item("a", "old", NOW - ContinueStore.EXPIRY_MS - 1));
        list.add(item("a", "fresh", NOW - 1000));
        ContinueStore.prune(list, NOW);
        assertEquals(1, list.size());
        assertEquals("fresh", list.get(0).id);
        assertFalse(ContinueStore.upsert(new ArrayList<>(), item("a", "x", NOW - ContinueStore.EXPIRY_MS - 1), NOW));
    }

    @Test
    public void removeOnlyTouchesCaller() {
        List<Item> list = new ArrayList<>();
        list.add(item("a", "1", NOW));
        list.add(item("a", "2", NOW));
        list.add(item("b", "1", NOW));
        assertEquals(1, ContinueStore.remove(list, "a", "1"));
        assertEquals(0, ContinueStore.remove(list, "a", "1"));
        assertEquals(1, ContinueStore.remove(list, "a", null));
        assertEquals(1, list.size());
        assertEquals("b", list.get(0).pkg);
    }

    @Test
    public void providerWinsOverMediaSession() {
        List<Item> list = new ArrayList<>();
        assertTrue(ContinueStore.upsert(list, media("a", "ms:1", NOW - 10), NOW));
        assertTrue(ContinueStore.upsert(list, item("a", "1", NOW - 5), NOW));
        assertEquals(1, list.size());
        assertEquals(ContinueStore.SOURCE_PROVIDER, list.get(0).source);
        assertFalse(ContinueStore.upsert(list, media("a", "ms:2", NOW), NOW));
        assertTrue(ContinueStore.upsert(list, media("b", "ms:2", NOW), NOW));
    }

    @Test
    public void visibleHidesFinishedAndBlocked() {
        List<Item> list = new ArrayList<>();
        list.add(item("a", "1", NOW - 3));
        list.add(new Item("a", "done", "D", null, null, null, null, 0.99f, 0, NOW, ContinueStore.SOURCE_PROVIDER));
        list.add(item("blocked", "1", NOW));
        list.add(item("a", "2", NOW - 1));
        List<Item> out = ContinueStore.visible(list, it -> !it.pkg.equals("blocked"), NOW);
        assertEquals(2, out.size());
        assertEquals("2", out.get(0).id);
    }

    @Test
    public void imageUrlNeedsHttp() {
        assertEquals("https://x.org/a.jpg", ContinueStore.imageUrl(" https://x.org/a.jpg "));
        assertNotNull(ContinueStore.imageUrl("http://x.org/a.jpg"));
        assertNull(ContinueStore.imageUrl("ftp://x.org/a.jpg"));
        assertNull(ContinueStore.imageUrl("file:///sdcard/a.jpg"));
        assertNull(ContinueStore.imageUrl("content://x/a"));
        assertNull(ContinueStore.imageUrl("https:///a.jpg"));
        assertNull(ContinueStore.imageUrl("https://x.org/a b.jpg"));
        assertNull(ContinueStore.imageUrl("https://"));
        StringBuilder longUrl = new StringBuilder("https://x.org/");
        while (longUrl.length() <= ContinueStore.URL_MAX) longUrl.append('a');
        assertNull(ContinueStore.imageUrl(longUrl.toString()));
    }

    @Test
    public void deepLinkAnySchemeButFile() {
        assertNotNull(ContinueStore.deepLink("owntv://play?id=42"));
        assertNotNull(ContinueStore.deepLink("https://www.youtube.com/watch?v=x"));
        assertNotNull(ContinueStore.deepLink("my-app+v2.x:open"));
        assertNull(ContinueStore.deepLink("file:///sdcard/x"));
        assertNull(ContinueStore.deepLink("FILE:///sdcard/x"));
        assertNull(ContinueStore.deepLink("no scheme"));
        assertNull(ContinueStore.deepLink("1abc://x"));
        assertNull(ContinueStore.deepLink("abc:"));
        assertNull(ContinueStore.deepLink(":x"));
    }

    @Test
    public void fromValuesChecksFields() {
        String[] why = new String[1];
        Item ok = ContinueStore.fromValues("a", "ep1", "  Show\nS1  ", "", "https://x.org/a.jpg", "app://x",
                1.7, -5L, NOW, why);
        assertNotNull(ok);
        assertEquals("Show S1", ok.title);
        assertNull(ok.subtitle);
        assertEquals(1f, ok.progress, 0f);
        assertEquals(0, ok.durationMs);
        assertEquals(NOW, ok.updatedAt);
        assertNull(ContinueStore.fromValues("a", "ep1", "T", null, "ftp://x", null, 0.5, null, NOW, why));
        assertEquals("bad image_url", why[0]);
        assertNull(ContinueStore.fromValues("a", "ep 1", "T", null, null, null, 0.5, null, NOW, why));
        assertNull(ContinueStore.fromValues("a", "ep1", "  ", null, null, null, 0.5, null, NOW, why));
        assertNull(ContinueStore.fromValues("a", "ep1", "T", null, null, "file:///x", 0.5, null, NOW, why));
        assertEquals(0f, ContinueStore.progress(Double.NaN), 0f);
    }

    @Test
    public void mediaMapping() {
        Item it = ContinueStore.fromMedia("p", null, "Episode 3", null, "The Show", "Album", null, "/c/a.jpg",
                30 * 60_000L, 60 * 60_000L, NOW);
        assertNotNull(it);
        assertEquals("Episode 3", it.title);
        assertEquals("The Show", it.subtitle);
        assertEquals(0.5f, it.progress, 0.001f);
        assertEquals("/c/a.jpg", it.imageFile);
        assertNull(it.deepLink);
        assertEquals(ContinueStore.SOURCE_MEDIA, it.source);
        Item display = ContinueStore.fromMedia("p", "Shown", "Raw", "Sub", "Artist", null,
                "https://x.org/a.jpg", "/c/a.jpg", 0, 0, NOW);
        assertEquals("Shown", display.title);
        assertEquals("Sub", display.subtitle);
        assertEquals("https://x.org/a.jpg", display.imageUrl);
        assertNull(display.imageFile);
        assertEquals(0f, display.progress, 0f);
        assertEquals(it.id, ContinueStore.fromMedia("p", null, "episode 3", null, null, null, null, null, 0, 0, NOW).id);
        assertNull(ContinueStore.fromMedia("p", null, null, null, null, null, null, null, 0, 0, NOW));
        assertNull(ContinueStore.fromMedia("p", null, "Song", null, null, null, null, null, 0, 90_000L, NOW));
    }

    @Test
    public void jsonRoundTrip() {
        List<Item> list = new ArrayList<>();
        list.add(new Item("a", "1", "T", "S", "https://x.org/a.jpg", null, "app://x", 0.25f, 1000, NOW,
                ContinueStore.SOURCE_PROVIDER));
        list.add(new Item("b", "ms:1", "M", null, null, "/c/x.jpg", null, 0.5f, 0, NOW - 1, ContinueStore.SOURCE_MEDIA));
        List<Item> back = ContinueStore.fromJson(ContinueStore.toJson(list));
        assertEquals(2, back.size());
        assertEquals("S", back.get(0).subtitle);
        assertEquals("app://x", back.get(0).deepLink);
        assertEquals(1000, back.get(0).durationMs);
        assertEquals("/c/x.jpg", back.get(1).imageFile);
        assertEquals(ContinueStore.SOURCE_MEDIA, back.get(1).source);
        assertTrue(ContinueStore.fromJson("not json").isEmpty());
    }

    @Test
    public void approvalPinnedToSigner() {
        String v = ContinueApps.value(ContinueApps.SHOW, "abc");
        assertEquals(ContinueApps.SHOW, ContinueApps.decision(v, "abc"));
        assertEquals(ContinueApps.WAIT, ContinueApps.decision(v, "def"));
        assertEquals(ContinueApps.HIDE, ContinueApps.decision(ContinueApps.value(ContinueApps.HIDE, "abc"), "abc"));
        assertEquals(ContinueApps.WAIT, ContinueApps.decision(null, "abc"));
        assertEquals(ContinueApps.WAIT, ContinueApps.decision("junk", "abc"));
    }
}
