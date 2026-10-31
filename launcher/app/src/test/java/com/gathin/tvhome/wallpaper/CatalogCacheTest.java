package com.gathin.tvhome.wallpaper;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CatalogCacheTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void freshEntryIsReturnedAndOldOneIsNot() throws Exception {
        CatalogCache cache = new CatalogCache(new File(tmp.getRoot(), "wallpaper_catalog"));
        assertNull(cache.get("chromecast"));
        cache.put("chromecast", "[1,2]");
        assertEquals("[1,2]", cache.get("chromecast"));
        long now = System.currentTimeMillis();
        assertEquals("[1,2]", cache.get("chromecast", now + CatalogCache.TTL_MS - 60_000));
        assertNull(cache.get("chromecast", now + CatalogCache.TTL_MS + 60_000));
        assertEquals("[1,2]", cache.getStale("chromecast"));
    }

    @Test public void unsafeNamesStayInsideTheDirectory() {
        File dir = new File(tmp.getRoot(), "c");
        CatalogCache cache = new CatalogCache(dir);
        cache.put("../evil/name", "x");
        assertEquals("x", cache.get("../evil/name"));
        assertEquals(1, dir.list().length);
    }

    @Test public void nullDirectoryIsNoCache() {
        CatalogCache cache = new CatalogCache(null);
        cache.put("a", "b");
        assertNull(cache.get("a"));
    }

    @Test public void loadUsesTheCacheThenTheNetworkThenStaleCache() throws Exception {
        CatalogCache cache = new CatalogCache(tmp.newFolder("cat"));
        FakeHttp http = new FakeHttp().on("https://x/", "{\"a\":1}");
        assertEquals("{\"a\":1}", CatalogCache.load(cache, "k", "https://x/y", null, http));
        assertEquals("{\"a\":1}", CatalogCache.load(cache, "k", "https://x/y", null, http));
        assertEquals(1, http.requests.size());   // the second call came from the cache

        // an expired entry plus a dead network: the stale text is better than nothing
        File file = new File(tmp.getRoot(), "cat/k.json");
        file.setLastModified(System.currentTimeMillis() - CatalogCache.TTL_MS - 1000);
        assertEquals("{\"a\":1}", CatalogCache.load(cache, "k", "https://down/", null, new FakeHttp()));
    }

    @Test(expected = ProviderException.class) public void loadFailsWhenThereIsNothingCached() throws Exception {
        CatalogCache.load(new CatalogCache(tmp.newFolder("empty")), "k", "https://down/", null, new FakeHttp());
    }
}
