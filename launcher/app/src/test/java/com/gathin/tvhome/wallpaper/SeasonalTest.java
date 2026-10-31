package com.gathin.tvhome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Map;

public class SeasonalTest {
    @Test
    public void holidaysGiveATermAndOrdinaryDaysDoNot() {
        assertEquals("christmas", Seasonal.query(LocalDate.of(2026, 12, 24)));
        assertEquals("fireworks", Seasonal.query(LocalDate.of(2026, 12, 31)));
        assertEquals("fireworks", Seasonal.query(LocalDate.of(2027, 1, 1)));
        assertEquals("bonfire", Seasonal.query(LocalDate.of(2026, 6, 24)));
        assertEquals("halloween", Seasonal.query(LocalDate.of(2026, 10, 31)));
        assertNull(Seasonal.query(LocalDate.of(2026, 3, 18)));
        assertNull(Seasonal.query(LocalDate.of(2026, 8, 5)));
    }

    @Test
    public void carnivalFollowsEaster() {
        assertEquals(LocalDate.of(2026, 4, 5), Seasonal.easter(2026));
        // Easter 2026 is 5 April: Carnival is 14 to 17 February, Ash Wednesday 18 February.
        assertEquals("carnival", Seasonal.query(LocalDate.of(2026, 2, 16)));
        assertEquals("carnival", Seasonal.query(LocalDate.of(2026, 2, 13)));
        assertNull(Seasonal.query(LocalDate.of(2026, 2, 19)));
        assertEquals(LocalDate.of(2025, 4, 20), Seasonal.easter(2025));
    }

    @Test
    public void wallhavenSearchesTheHolidayTermWithADailySeed() throws Exception {
        String url = WallhavenProvider.seasonalUrl("christmas", LocalDate.of(2026, 12, 24));
        assertTrue(url, url.contains("q=christmas"));
        assertTrue(url, url.contains("seed=261224"));
        assertTrue(url, url.contains("atleast=3840x2160"));
        FakeHttp http = new FakeHttp().on("https://wallhaven.cc/api/v1/search?q=christmas",
                "{\"data\":[{\"id\":\"abc\",\"path\":\"https://w.wallhaven.cc/full/ab/wallhaven-abc.jpg\","
                        + "\"url\":\"https://wallhaven.cc/w/abc\",\"file_size\":1000}]}");
        WallpaperItem item = new WallhavenProvider("christmas").fetch(LocalDate.of(2026, 12, 24), http);
        assertEquals("Wallhaven abc", item.title);
        assertEquals(1, http.requests.size());
    }

    @Test
    public void withoutATermWallhavenUsesTheTopList() throws Exception {
        FakeHttp http = new FakeHttp().on(WallhavenProvider.URL,
                "{\"data\":[{\"id\":\"x\",\"path\":\"https://w.wallhaven.cc/full/x.jpg\",\"url\":\"u\",\"file_size\":5}]}");
        new WallhavenProvider().fetch(LocalDate.of(2026, 3, 18), http);
        assertEquals(WallhavenProvider.URL, http.requests.get(0));
    }

    @Test
    public void theRegistryPassesTheTermToWallhaven() {
        Map<String, WallpaperProvider> map = WallpaperProviders.create(null, null, null, null, "halloween");
        FakeHttp http = new FakeHttp().on("https://wallhaven.cc/api/v1/search?q=halloween",
                "{\"data\":[{\"id\":\"h\",\"path\":\"https://w.wallhaven.cc/full/h.jpg\",\"url\":\"u\",\"file_size\":5}]}");
        try {
            map.get("wallhaven").fetch(LocalDate.of(2026, 10, 30), http);
        } catch (ProviderException e) {
            throw new AssertionError(e);
        }
        assertEquals(1, http.requests.size());
    }
}
