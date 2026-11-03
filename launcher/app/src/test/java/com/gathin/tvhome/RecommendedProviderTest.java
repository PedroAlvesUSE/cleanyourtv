package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RecommendedProviderTest {
    @Test
    public void recommendedShelfSkipsNewsAndAds() throws Exception {
        String json = "{"
                + "\"contents\":{\"tvBrowseRenderer\":{\"content\":{\"tvSurfaceContentRenderer\":{"
                + "\"content\":{\"sectionListRenderer\":{\"contents\":["
                + "{\"shelfRenderer\":{"
                + "\"headerRenderer\":{\"title\":{\"simpleText\":\"Recomendados\"}},"
                + "\"content\":{\"horizontalListRenderer\":{\"items\":["
                + "{\"adSlotRenderer\":{}},"
                + "{\"tileRenderer\":{\"contentType\":\"TILE_CONTENT_TYPE_VIDEO\",\"contentId\":\"abcdefghijk\","
                + "\"header\":{\"simpleText\":\"4:02\"},"
                + "\"metadata\":{\"line\":[{\"simpleText\":\"Um titulo\"},{\"simpleText\":\"Canal\"}]}}},"
                + "{\"tileRenderer\":{\"contentType\":\"TILE_CONTENT_TYPE_VIDEO\",\"contentId\":\"shortsshort\","
                + "\"metadata\":{\"simpleText\":\"Um short\"}}}"
                + "]}}}}"
                + ",{\"shelfRenderer\":{"
                + "\"headerRenderer\":{\"title\":{\"simpleText\":\"Principais notícias\"}},"
                + "\"content\":{\"horizontalListRenderer\":{\"items\":["
                + "{\"tileRenderer\":{\"contentType\":\"TILE_CONTENT_TYPE_VIDEO\",\"contentId\":\"zzzzzzzzzzz\","
                + "\"header\":{\"simpleText\":\"1:00\"},\"metadata\":{\"simpleText\":\"Noticia\"}}}"
                + "]}}}}"
                + "]}}}}}}}";
        java.util.List<RecommendedProvider.Item> items = RecommendedProvider.recommendedShelf(json);
        assertEquals(1, items.size());
        assertEquals("abcdefghijk", items.get(0).videoId);
        assertEquals("Um titulo", items.get(0).title);
        assertEquals("Canal", items.get(0).channel);
        assertEquals(242, items.get(0).durationSec);
        assertTrue(items.get(0).thumb.contains("abcdefghijk"));
    }

    @Test
    public void durationAcceptsHours() {
        assertEquals(0, RecommendedProvider.durationSeconds("há 7 horas"));
        assertEquals(3755, RecommendedProvider.durationSeconds("1:02:35"));
        assertEquals(0, RecommendedProvider.durationSeconds(null));
    }
}
