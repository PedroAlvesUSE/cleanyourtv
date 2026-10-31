package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoteApiTest {
    @Test
    public void pairUrlCarriesThePinInTheFragment() {
        assertEquals("http://192.168.3.21:8765/#pin=0420", RemoteApi.pairUrl("http://192.168.3.21:8765", "0420"));
        assertEquals("http://10.0.0.2:8766/#pin=1234", RemoteApi.pairUrl("http://10.0.0.2:8766/", "1234"));
        assertNull(RemoteApi.pairUrl(null, "1234"));
        assertNull(RemoteApi.pairUrl("http://10.0.0.2:8765", null));
    }

    @Test
    public void pairUrlFitsInTheQr() {
        String url = RemoteApi.pairUrl("http://192.168.100.200:65535", "9999");
        assertTrue(url.length() <= Qr.MAX_BYTES);
        assertNotNull(Qr.encode(url));
    }

    @Test
    public void mediaKeysMapToAndroidKeycodes() {
        assertEquals(85, RemoteApi.mediaKeyCode("playpause"));
        assertEquals(87, RemoteApi.mediaKeyCode("next"));
        assertEquals(88, RemoteApi.mediaKeyCode("prev"));
        assertEquals(89, RemoteApi.mediaKeyCode("rewind"));
        assertEquals(90, RemoteApi.mediaKeyCode("forward"));
        assertEquals(0, RemoteApi.mediaKeyCode("up"));
        assertEquals(0, RemoteApi.mediaKeyCode(null));
    }

    @Test
    public void packageNamesAreValidated() {
        assertTrue(RemoteApi.validPackage("io.gh.reisxd.tizentube.cobalt"));
        assertTrue(RemoteApi.validPackage("com.google.android.youtube.tv"));
        assertFalse(RemoteApi.validPackage(null));
        assertFalse(RemoteApi.validPackage("noDots"));
        assertFalse(RemoteApi.validPackage("com..x"));
        assertFalse(RemoteApi.validPackage("com.x/../y"));
        assertFalse(RemoteApi.validPackage("1com.x"));
        assertFalse(RemoteApi.validPackage("com.x;rm"));
    }

    @Test
    public void searchTermIsTrimmedAndBounded() {
        assertEquals("lofi beats", RemoteApi.searchTerm("  lofi beats \n"));
        assertNull(RemoteApi.searchTerm(""));
        assertNull(RemoteApi.searchTerm("   "));
        assertNull(RemoteApi.searchTerm(null));
        assertNull(RemoteApi.searchTerm("a\nb"));
        char[] longTerm = new char[RemoteApi.MAX_SEARCH + 1];
        Arrays.fill(longTerm, 'a');
        assertNull(RemoteApi.searchTerm(new String(longTerm)));
    }

    @Test
    public void appsJsonEscapesLabels() {
        assertEquals("[]", RemoteApi.appsJson(Collections.emptyList()));
        assertEquals("[{\"p\":\"a.b\",\"n\":\"Say \\\"hi\\\"\"},{\"p\":\"c.d\",\"n\":\"\\u003cx\\u003e\\\\\"}]",
                RemoteApi.appsJson(Arrays.asList(new String[]{"a.b", "Say \"hi\""}, new String[]{"c.d", "<x>\\"})));
        assertEquals("\"\\u2028\\n\"", RemoteApi.json("\u2028\n"));
    }

    @Test
    public void pagesReadThePinFromTheUrlAndClearIt() {
        for (String page : new String[]{WebPages.remote(), WebPages.backup()}) {
            assertTrue(page.contains(WebPages.PIN_FROM_URL));
            assertTrue(page.contains("history.replaceState"));
            assertTrue(page.contains("localStorage.setItem('pin'"));
        }
    }

    @Test
    public void everyRemoteButtonIsAcceptedByTheService() {
        Matcher m = Pattern.compile("data-k=([a-z]+)").matcher(WebPages.remote());
        int n = 0;
        while (m.find()) {
            assertTrue(m.group(1), HomeKeyService.REMOTE_KEYS.contains(m.group(1)));
            n++;
        }
        assertEquals(8, n);
    }

    @Test
    public void remotePageIsMobileFriendly() {
        String page = WebPages.remote();
        assertTrue(page.contains("viewport-fit=cover"));
        assertTrue(page.contains("user-scalable=no"));
        assertTrue(page.contains("safe-area-inset-bottom"));
        assertTrue(page.contains("navigator.vibrate"));
        assertTrue(page.contains("prefers-reduced-motion"));
    }
}
