package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class WallpaperUrlsTest {

    @Test
    public void parseKeepsHttpsAndHttpOnePerLineDropsJunk() {
        assertEquals(Arrays.asList(
                        "https://example.com/a.jpg",
                        "http://192.168.1.10/wall.png"),
                WallpaperUrls.parseForTest(
                        "https://example.com/a.jpg\n"
                                + "not-a-url\n"
                                + "http://192.168.1.10/wall.png\n"
                                + "ftp://nope/x.jpg\n"
                                + "https://example.com/a.jpg\n"));
        assertTrue(WallpaperUrls.parseForTest("").isEmpty());
        assertTrue(WallpaperUrls.parseForTest(null).isEmpty());
    }

    @Test
    public void parseCapsAtSixUrls() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append("https://example.com/").append(i).append(".jpg\n");
        assertEquals(6, WallpaperUrls.parseForTest(sb.toString()).size());
    }

    @Test
    public void validRejectsSpacesAndNonHttp() {
        assertTrue(WallpaperUrls.valid("https://a/b.jpg"));
        assertFalse(WallpaperUrls.valid("https://a /b.jpg"));
        assertFalse(WallpaperUrls.valid("file:///tmp/a.jpg"));
        assertFalse(WallpaperUrls.valid(null));
    }

    @Test
    public void parseEmptyLinesIgnored() {
        assertEquals(Collections.singletonList("https://x/y.webp"),
                WallpaperUrls.parseForTest("\n  \nhttps://x/y.webp\n"));
    }

    @Test
    public void limitsAreRaisedForFourKPictures() {
        assertEquals(12 * 1024 * 1024, WallpaperUrls.MAX_BYTES);
        assertEquals(48L * 1024 * 1024, WallpaperUrls.MAX_CACHE_BYTES);
    }

    @Test
    public void downloadIntoGoesOnWhenOneUrlFails() throws IOException {
        File dir = Files.createTempDirectory("wall").toFile();
        List<String> urls = Arrays.asList("https://a/1.jpg", "https://a/2.jpg", "https://a/3.jpg");
        List<String> tried = new ArrayList<>();
        String err = WallpaperUrls.downloadInto(urls, dir, (url, dest) -> {
            tried.add(url);
            if (url.endsWith("2.jpg")) return "http 404";
            try {
                Files.write(dest.toPath(), new byte[]{1, 2, 3});
            } catch (IOException e) {
                return "save failed";
            }
            return null;
        });
        assertEquals(null, err);
        assertEquals(urls, tried);
        assertTrue(WallpaperUrls.fileIn(dir, urls.get(0)).isFile());
        assertFalse(WallpaperUrls.fileIn(dir, urls.get(1)).exists());
        assertTrue(WallpaperUrls.fileIn(dir, urls.get(2)).isFile());
    }

    @Test
    public void downloadIntoReportsTheFirstErrorWhenEveryUrlFails() throws IOException {
        File dir = Files.createTempDirectory("wall").toFile();
        String err = WallpaperUrls.downloadInto(Arrays.asList("https://a/1.jpg", "https://a/2.jpg"), dir,
                (url, dest) -> url.endsWith("1.jpg") ? "http 500" : "too large");
        assertEquals("http 500", err);
    }

    @Test
    public void downloadIntoKeepsFilesAlreadyCached() throws IOException {
        File dir = Files.createTempDirectory("wall").toFile();
        File cached = WallpaperUrls.fileIn(dir, "https://a/1.jpg");
        Files.write(cached.toPath(), new byte[]{9});
        List<String> tried = new ArrayList<>();
        assertEquals(null, WallpaperUrls.downloadInto(Collections.singletonList("https://a/1.jpg"), dir,
                (url, dest) -> {
                    tried.add(url);
                    return "should not run";
                }));
        assertTrue(tried.isEmpty());
    }

    @Test
    public void pruneToBudgetDeletesTheOldestFilesFirst() throws IOException {
        File dir = Files.createTempDirectory("wall").toFile();
        File old = new File(dir, "old.img");
        File fresh = new File(dir, "fresh.img");
        Files.write(old.toPath(), new byte[600]);
        Files.write(fresh.toPath(), new byte[600]);
        assertTrue(old.setLastModified(1_000_000L));
        assertTrue(fresh.setLastModified(2_000_000L));
        WallpaperUrls.pruneToBudget(dir, 1000);
        assertFalse(old.exists());
        assertTrue(fresh.exists());
    }
}
