package com.gathin.tvhome.wallpaper;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** One parse test per source, on trimmed copies of the real answers (launcher/app/src/test/resources/wallpaper). */
public class ProvidersParseTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 30);

    private static void assertFails(WallpaperProvider provider, Http http, String messagePart) {
        try {
            provider.fetch(DAY, http);
            fail("expected ProviderException");
        } catch (ProviderException e) {
            assertTrue("message was: " + e.getMessage(), e.getMessage().contains(messagePart));
        }
    }

    @Test public void bing() throws Exception {
        FakeHttp http = new FakeHttp().onSample(BingProvider.URL, "bing");
        WallpaperItem item = new BingProvider().fetch(DAY, http);
        assertEquals("bing", item.provider);
        assertEquals("https://www.bing.com/th?id=OHR.AutumnCranes_PT-BR8749482923_UHD.jpg", item.urlUhd);
        assertEquals("https://www.bing.com/th?id=OHR.AutumnCranes_PT-BR8749482923_1920x1080.jpg", item.urlHd);
        assertEquals("É tempo de levantar voo", item.title);
        assertTrue(item.copyright.contains("Terry St.Louis"));
        assertEquals("2026-10-30", item.date);
        assertNull(item.urlPortrait);
    }

    @Test public void bingWithoutImagesFails() {
        assertFails(new BingProvider(), new FakeHttp().on(BingProvider.URL, "{\"images\":[]}"), "urlbase");
    }

    @Test public void spotlightReadsTheNestedJson() throws Exception {
        WallpaperItem item = new SpotlightProvider().fetch(DAY, new FakeHttp().onSample(SpotlightProvider.URL, "spotlight"));
        assertEquals("spotlight", item.provider);
        assertTrue(item.urlUhd, item.urlUhd.endsWith("_wl_santiagocaboverde_shutterstock_2369624621_1920x1080.jpg"));
        assertTrue(item.urlPortrait, item.urlPortrait.endsWith("_1080x1920.jpg"));
        assertEquals("Santiago, Cabo Verde", item.title);
        assertEquals("© Curioso.Photography / Shutterstock", item.copyright);
    }

    @Test public void spotlightWithoutItemsFails() {
        assertFails(new SpotlightProvider(), new FakeHttp().on(SpotlightProvider.URL, "{\"batchrsp\":{\"items\":[]}}"), "no items");
    }

    @Test public void peapixBing() throws Exception {
        WallpaperItem item = PeapixProvider.bing().fetch(DAY, new FakeHttp().onSample(PeapixProvider.BING_URL, "peapix_bing"));
        assertEquals("peapix_bing", item.provider);
        assertEquals("https://img.peapix.com/88f478fa2b5e48dcafd313ab463478a6_1920.jpg", item.urlHd);
        assertEquals("https://img.peapix.com/88f478fa2b5e48dcafd313ab463478a6.jpg", item.urlUhd);
        assertTrue(item.title.startsWith("Grous-canadenses"));
        assertEquals("© Terry St.Louis/Getty Image", item.copyright);
    }

    @Test public void peapixSpotlight() throws Exception {
        WallpaperItem item = PeapixProvider.spotlight().fetch(DAY, new FakeHttp().onSample(PeapixProvider.SPOTLIGHT_URL, "peapix_spotlight"));
        assertEquals("peapix_spotlight", item.provider);
        assertEquals("Illuminated Gwangan Bridge, Busan, South Korea", item.title);
        assertEquals("https://img.peapix.com/fb8b03f7aff74e57bfcc211af5237bd3.jpg", item.urlUhd);
    }

    @Test public void biturl() throws Exception {
        WallpaperItem item = new BiturlProvider().fetch(DAY, new FakeHttp().onSample(BiturlProvider.URL, "biturl"));
        assertEquals("https://www.bing.com/th?id=OHR.AutumnCranes_PT-BR8749482923_UHD.jpg", item.urlUhd);
        assertEquals("https://www.bing.com/th?id=OHR.AutumnCranes_PT-BR8749482923_1920x1080.jpg", item.urlHd);
        assertEquals("Grous-canadenses, Refúgio Nacional de Vida Selvagem Bosque del Apache, Novo México, Estados Unidos",
                item.title);
        assertTrue(item.copyright.endsWith("(© Terry St.Louis/Getty Images)"));
    }

    @Test public void chromecastPicksByTheDailySeedAndAsksForFourK() throws Exception {
        FakeHttp http = new FakeHttp().onSample(ChromecastProvider.URL, "chromecast");
        WallpaperItem item = new ChromecastProvider(null).fetch(DAY, http);
        String[] authors = {"Ziv Horesh", "Romain Guy", "Alistair Nicol", "Patrick Smith", "Trey Ratcliff"};
        assertEquals("© " + authors[DailySeed.index(DAY, "chromecast", 5)], item.copyright);
        assertTrue(item.urlUhd, item.urlUhd.contains("/s3840-w3840-h2160-c/"));
        assertTrue(item.urlHd, item.urlHd.contains("/s1920-w1920-h1080-c/"));
        assertEquals(item.urlHd.replace("1920", "3840").replace("1080", "2160"), item.urlUhd);
    }

    @Test public void chromecastSizeTailsAreRewrittenWithCare() {
        String path = "https://lh3.googleusercontent.com/-a/b/c/d/s1920-w1920-h1080-c/x%2By.jpg";
        assertEquals("https://lh3.googleusercontent.com/-a/b/c/d/s3840-w3840-h2160-c/x%2By.jpg",
                ChromecastProvider.resize(path, 3840, 2160));
        assertEquals("https://lh3.googleusercontent.com/abc=w3840-h2160-c",
                ChromecastProvider.resize("https://lh3.googleusercontent.com/abc=s1280", 3840, 2160));
        assertEquals("https://lh3.googleusercontent.com/abc=w3840-h2160-c",
                ChromecastProvider.resize("https://lh3.googleusercontent.com/abc=w1280-h720-no", 3840, 2160));
        // no known size tail, or another host: untouched
        assertEquals("https://lh3.googleusercontent.com/abc", ChromecastProvider.resize("https://lh3.googleusercontent.com/abc", 3840, 2160));
        assertEquals("https://example.com/s1920-w1920-h1080-c/a.jpg",
                ChromecastProvider.resize("https://example.com/s1920-w1920-h1080-c/a.jpg", 3840, 2160));
    }

    @Test public void chromecastSameDayGivesSamePicture() throws Exception {
        FakeHttp http = new FakeHttp().onSample(ChromecastProvider.URL, "chromecast");
        assertEquals(new ChromecastProvider(null).fetch(DAY, http).urlUhd, new ChromecastProvider(null).fetch(DAY, http).urlUhd);
    }

    @Test public void earthViewBuildsTheTitleFromRegionAndCountry() throws Exception {
        FakeHttp http = new FakeHttp().onSample(EarthViewProvider.URL, "earthview");
        String[] titles = {"Australia", "Tamarugal, Chile", "Ngari, China", "Ngari, China", "Ngari, China"};
        for (int d = 0; d < 6; d++) {
            LocalDate day = DAY.plusDays(d);
            WallpaperItem item = new EarthViewProvider(null).fetch(day, http);
            assertEquals(titles[DailySeed.index(day, "earthview", 5)], item.title);
            assertTrue(item.urlUhd.startsWith("https://www.gstatic.com/prettyearth/assets/full/"));
            assertEquals("Google Earth View", item.copyright);
        }
    }

    @Test public void wikimediaAsksTheThumbnailServiceForFourK() throws Exception {
        FakeHttp http = new FakeHttp().onSample("https://api.wikimedia.org/feed/v1/wikipedia/en/featured/2026/10/30", "wikimedia");
        WallpaperItem item = new WikimediaProvider().fetch(DAY, http);
        assertEquals("Sunrays shining through trees and weak fog near Walchwil, Switzerland", item.title);
        assertTrue(item.urlUhd, item.urlUhd.contains("/thumb/2/25/") && item.urlUhd.contains("/3840px-CH.ZG.Walchwil"));
        assertTrue(item.urlHd, item.urlHd.contains("/1920px-CH.ZG.Walchwil"));
        assertTrue(item.copyright, item.copyright.startsWith("NOTE: This image is a panorama"));
        assertEquals(1, http.requests.size());
    }

    @Test public void wikimediaUrlHasTheDateWithTwoDigits() {
        assertEquals("https://api.wikimedia.org/feed/v1/wikipedia/en/featured/2026/03/07", WikimediaProvider.url(LocalDate.of(2026, 3, 7)));
    }

    @Test public void wikimediaWithoutAnImageFails() {
        assertFails(new WikimediaProvider(), new FakeHttp().on("https://api.wikimedia.org/", "{\"tfa\":{}}"), "no featured image");
    }

    @Test public void nasaImageUsesHdurl() throws Exception {
        FakeHttp http = new FakeHttp().onSample("https://api.nasa.gov/planetary/apod?api_key=DEMO_KEY", "nasa_image");
        WallpaperItem item = new NasaApodProvider(null).fetch(DAY, http);
        assertEquals("https://apod.nasa.gov/apod/image/2405/NightSkyGlow_4000.jpg", item.urlUhd);
        assertEquals("https://apod.nasa.gov/apod/image/2405/NightSkyGlow_1024.jpg", item.urlHd);
        assertEquals("Night Sky Glow over the Desert", item.title);
        assertEquals("© Babak Tafreshi", item.copyright);
    }

    @Test public void nasaUsesTheConfiguredKey() throws Exception {
        FakeHttp http = new FakeHttp().onSample("https://api.nasa.gov/planetary/apod?api_key=MYKEY", "nasa_image");
        new NasaApodProvider("MYKEY").fetch(DAY, http);
        assertEquals("https://api.nasa.gov/planetary/apod?api_key=MYKEY", http.requests.get(0));
    }

    @Test public void nasaVideoFails() {
        assertFails(new NasaApodProvider(null), new FakeHttp().onSample("https://api.nasa.gov/", "nasa_video"), "not an image");
    }

    @Test public void nasaLogoPlaceholderFails() {
        assertFails(new NasaApodProvider(null), new FakeHttp().onSample("https://api.nasa.gov/", "nasa_logo"), "NASA logo");
    }

    @Test public void nasaServerErrorBodyFails() {
        assertFails(new NasaApodProvider(null), new FakeHttp().on("https://api.nasa.gov/", "{\"code\":500,\"msg\":\"Internal Service Error\"}"), "not an image");
    }

    @Test public void articBuildsIiifUrlsAndKeepsTheHeader() throws Exception {
        FakeHttp http = new FakeHttp().onSample("https://api.artic.edu/api/v1/artworks/search", "artic");
        WallpaperItem item = new ArticProvider().fetch(DAY, http);
        assertEquals("https://www.artic.edu/iiif/2/ea8c5d62-6ce8-88e8-feb1-e0053cf534c5/full/1686,/0/default.jpg", item.urlUhd);
        assertEquals("https://www.artic.edu/iiif/2/ea8c5d62-6ce8-88e8-feb1-e0053cf534c5/full/843,/0/default.jpg", item.urlHd);
        assertEquals("Coronation Stone of Moctezuma Xocoyotzin", item.title);
        assertEquals("Mexica (Aztec)", item.copyright);
        assertTrue(item.imageHeaders.containsKey("AIC-User-Agent"));
        // the API call carries it too
        assertTrue(http.requestHeaders.get(0).containsKey("AIC-User-Agent"));
    }

    @Test public void articPageComesFromTheSeedAndStaysInTheAllowedWindow() {
        for (int d = 0; d < 200; d++) {
            String url = ArticProvider.url(DAY.plusDays(d));
            int page = Integer.parseInt(url.replaceAll(".*[?&]page=(\\d+).*", "$1"));
            assertTrue(page >= 1 && page <= 1000);
        }
        assertEquals(ArticProvider.url(DAY), ArticProvider.url(DAY));
    }

    @Test public void metPicksAnIdBySeedThenReadsTheObject() throws Exception {
        FakeHttp http = new FakeHttp()
                .onSample(MetProvider.SEARCH_URL, "met_search")
                .onSample(MetProvider.OBJECT_URL, "met_object");
        WallpaperItem item = new MetProvider(null).fetch(DAY, http);
        assertEquals("Wheat Field with Cypresses", item.title);
        assertEquals("https://images.metmuseum.org/CRDImages/ep/original/DP-42549-001.jpg", item.urlUhd);
        assertEquals("https://images.metmuseum.org/CRDImages/ep/web-large/DP-42549-001.jpg", item.urlHd);
        assertEquals("Vincent van Gogh, 1889", item.copyright);
        assertEquals(MetProvider.SEARCH_URL, http.requests.get(0));
        String[] ids = FakeHttp.sample("met_search").replaceAll("(?s).*\"objectIDs\":\\s*\\[(.*?)\\].*", "$1").split(",\\s*");
        assertEquals(MetProvider.OBJECT_URL + ids[DailySeed.index(DAY, "met", ids.length)].trim(), http.requests.get(1));
    }

    @Test public void metSkipsObjectsWithoutAPicture() throws Exception {
        FakeHttp http = new FakeHttp()
                .onSample(MetProvider.SEARCH_URL, "met_search")
                .on(MetProvider.OBJECT_URL, "{\"objectID\":1,\"isPublicDomain\":true,\"primaryImage\":\"\",\"title\":\"x\"}");
        assertFails(new MetProvider(null), http, "no public picture");
        assertEquals(1 + 4, http.requests.size());   // the id list plus four tries
    }

    @Test public void metSearchUsesTheNewV11Endpoint() {
        assertTrue(MetProvider.SEARCH_URL.contains("/v1.1/search"));
    }

    @Test public void redditPicksTheFirstSafeLandscapeImagePost() throws Exception {
        RedditProvider provider = RedditProvider.earthPorn();
        FakeHttp http = new FakeHttp().onSample("https://www.reddit.com/r/EarthPorn/top.json", "reddit");
        WallpaperItem item = provider.fetch(DAY, http);
        assertEquals("reddit_earthporn", item.provider);
        assertEquals("Lago di Braies at dawn, Dolomites [OC] [6000x4000]", item.title);
        assertEquals("https://i.redd.it/lagobraies333.jpg", item.urlHd);
        // the 6000 px preview source wins for UHD, with &amp; undone
        assertEquals("https://preview.redd.it/lagobraies333.jpg?width=6000&format=pjpg&auto=webp&s=abc123", item.urlUhd);
        assertEquals("u/mountainfan · r/EarthPorn", item.copyright);
        assertTrue(http.requestHeaders.get(0).get("User-Agent").startsWith("android:com.gathin.tvhome"));
        assertTrue(http.requests.get(0).contains("t=day&limit=10"));
    }

    @Test public void redditSmallPreviewFallsBackToTheOriginalImage() throws Exception {
        FakeHttp http = new FakeHttp().onSample("https://www.reddit.com/r/wallpapers/top.json", "reddit_small");
        WallpaperItem item = RedditProvider.wallpapers().fetch(DAY, http);
        assertEquals("https://i.redd.it/pines444.jpg", item.urlUhd);
        assertEquals("https://i.redd.it/pines444.jpg", item.urlHd);
    }

    @Test public void redditThreeSubredditsHaveOwnIds() {
        assertEquals("reddit_wallpapers", RedditProvider.wallpapers().id());
        assertEquals("reddit_amoledbackgrounds", RedditProvider.amoledBackgrounds().id());
        assertTrue(RedditProvider.amoledBackgrounds().url().contains("/r/AmoledBackgrounds/"));
    }

    @Test public void redditWithOnlyUnsuitablePostsFails() {
        FakeHttp http = new FakeHttp().on("https://www.reddit.com/", "{\"data\":{\"children\":[]}}");
        assertFails(RedditProvider.earthPorn(), http, "no suitable image post");
    }

    @Test public void wallhavenSkipsFilesOverTwelveMegabytes() throws Exception {
        WallpaperItem item = new WallhavenProvider().fetch(DAY, new FakeHttp().onSample(WallhavenProvider.URL, "wallhaven"));
        // the sample's first two files are 13 MB and 16 MB
        assertEquals("https://w.wallhaven.cc/full/rq/wallhaven-rqw7ej.jpg", item.urlUhd);
        assertEquals("Wallhaven rqw7ej", item.title);
        assertEquals("https://wallhaven.cc/w/rqw7ej", item.copyright);
    }

    @Test public void picsumNeedsNoNetwork() throws Exception {
        FakeHttp http = new FakeHttp();
        WallpaperItem item = new PicsumProvider().fetch(DAY, http);
        assertEquals("https://picsum.photos/seed/2026-10-30/3840/2160", item.urlUhd);
        assertEquals("https://picsum.photos/seed/2026-10-30/1920/1080", item.urlHd);
        assertTrue(http.requests.isEmpty());
    }

    @Test public void unsplashWithoutKeyFails() {
        assertFails(new UnsplashProvider(null), new FakeHttp(), "no key");
        assertFails(new UnsplashProvider("  "), new FakeHttp(), "no key");
    }

    @Test public void unsplashSendsTheClientIdAndAsksForFourK() throws Exception {
        FakeHttp http = new FakeHttp().onSample(UnsplashProvider.URL, "unsplash");
        WallpaperItem item = new UnsplashProvider("k123").fetch(DAY, http);
        Map<String, String> headers = http.requestHeaders.get(0);
        assertEquals("Client-ID k123", headers.get("Authorization"));
        assertEquals("https://images.unsplash.com/photo-1417325384643-aac51acc9e5d?ixid=M3w1&ixlib=rb-4.0.3&w=3840&fm=jpg&q=80", item.urlUhd);
        assertTrue(item.urlHd.contains("&w=1920"));
        assertEquals("A man drinking a coffee.", item.title);
        assertEquals("Photo by Example Photographer on Unsplash", item.copyright);
    }

    @Test public void unsplashHandlesNullTexts() throws Exception {
        WallpaperItem item = new UnsplashProvider("k").fetch(DAY, new FakeHttp().onSample(UnsplashProvider.URL, "unsplash_nodesc"));
        assertEquals("", item.title);
        assertEquals("Unsplash", item.copyright);   // not the text "null"
    }

    @Test public void pexelsWithoutKeyFails() {
        assertFails(new PexelsProvider(""), new FakeHttp(), "no key");
    }

    @Test public void pexelsSendsTheKeyAndResizesTheOriginal() throws Exception {
        FakeHttp http = new FakeHttp().onSample(PexelsProvider.URL, "pexels");
        WallpaperItem item = new PexelsProvider("pk").fetch(DAY, http);
        assertEquals("pk", http.requestHeaders.get(0).get("Authorization"));
        assertTrue(item.urlUhd, item.urlUhd.startsWith("https://images.pexels.com/photos/2014422/pexels-photo-2014422.jpeg?"));
        assertTrue(item.urlUhd.contains("w=3840&h=2160"));
        assertTrue(item.urlHd.contains("w=1920&h=1080"));
        assertEquals("Brown rocks during golden hour", item.title);
        assertEquals("Photo by Joey Farina on Pexels", item.copyright);
    }

    @Test public void invalidJsonIsAProviderException() {
        assertFails(new BingProvider(), new FakeHttp().on(BingProvider.URL, "<html>blocked</html>"), "invalid JSON");
    }
}
