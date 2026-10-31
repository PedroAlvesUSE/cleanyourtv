package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class LanServerTest {
    private static LanServer.Request read(String raw) throws Exception {
        return LanServer.read(new ByteArrayInputStream(raw.getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    public void parsesPathQueryHeadersAndBody() throws Exception {
        LanServer.Request r = read("POST /api/key?k=up&x=a%20b HTTP/1.1\r\nHost: tv\r\nX-Pin: 1234\r\n"
                + "Content-Length: 5\r\n\r\nhello");
        assertNotNull(r);
        assertEquals("POST", r.method);
        assertEquals("/api/key", r.path);
        assertEquals("up", r.query.get("k"));
        assertEquals("a b", r.query.get("x"));
        assertEquals("1234", r.headers.get("x-pin"));
        assertEquals("hello", r.bodyText());
    }

    @Test
    public void rejectsBadOrOversizedRequests() throws Exception {
        assertNull(read(""));
        assertNull(read("GARBAGE\r\n\r\n"));
        assertNull(read("POST / HTTP/1.1\r\nContent-Length: 99999999\r\n\r\n"));
        assertNull(read("POST / HTTP/1.1\r\nContent-Length: abc\r\n\r\n"));
        assertNull("body shorter than declared", read("POST / HTTP/1.1\r\nContent-Length: 10\r\n\r\nabc"));
    }

    @Test
    public void onlyPrivateAddressesAreServed() throws Exception {
        assertTrue(LanServer.isLan(InetAddress.getByName("192.168.3.50")));
        assertTrue(LanServer.isLan(InetAddress.getByName("10.0.0.7")));
        assertTrue(LanServer.isLan(InetAddress.getByName("172.20.1.1")));
        assertTrue(LanServer.isLan(InetAddress.getByName("169.254.1.1")));
        assertTrue(LanServer.isLan(InetAddress.getByName("127.0.0.1")));
        assertTrue(LanServer.isLan(InetAddress.getByName("fd12:3456::1")));
        assertFalse(LanServer.isLan(InetAddress.getByName("8.8.8.8")));
        assertFalse(LanServer.isLan(InetAddress.getByName("172.32.0.1")));
        assertFalse(LanServer.isLan(InetAddress.getByName("2001:4860:4860::8888")));
        assertFalse(LanServer.isLan(null));
    }
}
