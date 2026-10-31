package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhome.wallpaper.WallpaperItem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class SourcesWallpaperTest {
    private ServerSocket server;
    private String base;
    private final List<String> hits = new CopyOnWriteArrayList<>();
    private final List<String> auth = new CopyOnWriteArrayList<>();

    /** A tiny HTTP server on a socket: /uhd.jpg answers 404, /hd.jpg a 2 KB image, /huge.jpg declares 12 MB + 1. */
    @Before
    public void start() throws IOException {
        server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    handle(socket);
                } catch (IOException ignored) {
                    // closed by stop()
                }
            }
        });
        t.setDaemon(true);
        t.start();
        base = "http://127.0.0.1:" + server.getLocalPort();
    }

    private void handle(Socket socket) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
        String request = in.readLine();
        String header = null;
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            if (line.toLowerCase(java.util.Locale.US).startsWith("authorization:")) header = line.substring(14).trim();
        }
        String path = request.split(" ")[1];
        OutputStream out = socket.getOutputStream();
        if (path.equals("/uhd.jpg")) {
            hits.add("uhd");
            auth.add(String.valueOf(header));
            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        } else if (path.equals("/hd.jpg")) {
            hits.add("hd");
            auth.add(String.valueOf(header));
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: 2048\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.write(new byte[2048]);
        } else {
            hits.add("huge");
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: " + (WallpaperUrls.MAX_BYTES + 1L)
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        out.flush();
    }

    @After
    public void stop() throws IOException {
        server.close();
    }

    private static WallpaperItem item(String hd, String uhd, Map<String, String> headers) {
        return new WallpaperItem("test", "Title", hd, uhd, null, "Credit", "2026-10-30", headers);
    }

    @Test
    public void fallsBackToTheHdUrlWhenTheUhdOneFailsAndSendsTheHeaders() throws IOException {
        File dir = Files.createTempDirectory("src").toFile();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Client-ID abc");
        WallpaperItem item = item(base + "/hd.jpg", base + "/uhd.jpg", headers);
        File dest = SourcesWallpaper.fileFor(dir, item);
        assertNull(SourcesWallpaper.downloadItem(item, dest));
        assertEquals(2048, dest.length());
        assertEquals(java.util.Arrays.asList("uhd", "hd"), hits);
        assertEquals(java.util.Arrays.asList("Client-ID abc", "Client-ID abc"), auth);
    }

    @Test
    public void fallsBackToTheHdUrlWhenTheUhdFileIsOverTheLimit() throws IOException {
        File dir = Files.createTempDirectory("src").toFile();
        WallpaperItem item = item(base + "/hd.jpg", base + "/huge.jpg", Collections.<String, String>emptyMap());
        File dest = SourcesWallpaper.fileFor(dir, item);
        assertNull(SourcesWallpaper.downloadItem(item, dest));
        assertEquals(java.util.Arrays.asList("huge", "hd"), hits);
        assertTrue(dest.isFile());
    }

    @Test
    public void reportsTheErrorWhenBothUrlsFail() throws IOException {
        File dir = Files.createTempDirectory("src").toFile();
        WallpaperItem item = item(base + "/uhd.jpg", base + "/huge.jpg", null);
        File dest = SourcesWallpaper.fileFor(dir, item);
        assertEquals("http 404", SourcesWallpaper.downloadItem(item, dest));
        assertFalse(dest.exists());
    }

    @Test
    public void metaKeepsTitleAndCreditInOrder() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"a.img", "Lake\nMorning", "Photo by Ana", "unsplash"});
        rows.add(new String[]{"b.img", "", "NASA", "nasa"});
        List<String[]> back = SourcesWallpaper.parseMeta(SourcesWallpaper.formatMeta(rows));
        assertEquals(2, back.size());
        assertArrayEquals(new String[]{"a.img", "Lake Morning", "Photo by Ana", "unsplash"}, back.get(0));
        assertArrayEquals(new String[]{"b.img", "", "NASA", "nasa"}, back.get(1));
    }

    @Test
    public void pictureFilesLiveInTheSourcesFolder() {
        assertTrue(SourcesWallpaper.isCached(new File("/data/files/wallpaper_sources/x.img")));
        assertFalse(SourcesWallpaper.isCached(new File("/data/files/wallpaper_url/x.img")));
    }
}
