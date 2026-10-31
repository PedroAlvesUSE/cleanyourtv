package com.gathin.tvhome;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mini servidor HTTP só para a rede local, usado pelo backup e pelo controle pelo celular. Atende apenas
 * endereços privados (10/8, 172.16/12, 192.168/16, link local e loopback); qualquer outro é fechado sem resposta.
 * Cada servidor só roda enquanto quem o criou o mantém ligado.
 */
final class LanServer {
    static final int MAX_BODY = BackupData.MAX_BYTES + 1024;

    interface Handler {
        Response handle(Request request);
    }

    static final class Request {
        final String method;
        final String path;
        final Map<String, String> query;
        final Map<String, String> headers;
        final byte[] body;

        Request(String method, String path, Map<String, String> query, Map<String, String> headers, byte[] body) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.headers = headers;
            this.body = body;
        }

        String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    static final class Response {
        final int code;
        final String type;
        final byte[] body;

        Response(int code, String type, byte[] body) {
            this.code = code;
            this.type = type;
            this.body = body;
        }

        static Response text(int code, String type, String body) {
            return new Response(code, type + "; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
        }

        static Response ok(String text) {
            return text(200, "text/plain", text);
        }

        static Response error(int code, String text) {
            return text(code, "text/plain", text);
        }
    }

    private final Handler handler;
    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "lan-server");
        t.setDaemon(true);
        return t;
    });
    private volatile ServerSocket socket;
    private int port;

    LanServer(Handler handler) {
        this.handler = handler;
    }

    /** Liga na primeira porta livre a partir de {@code first}. Devolve a porta. */
    synchronized int start(int first) throws IOException {
        if (socket != null) return port;
        for (int p = first; p < first + 8; p++) {
            try {
                socket = new ServerSocket(p, 8);
                port = p;
                Thread t = new Thread(this::accept, "lan-accept");
                t.setDaemon(true);
                t.start();
                return p;
            } catch (BindException ignored) {
            }
        }
        throw new BindException("no free port");
    }

    synchronized void stop() {
        ServerSocket s = socket;
        socket = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
        pool.shutdownNow();
    }

    boolean running() {
        return socket != null;
    }

    private void accept() {
        ServerSocket s = socket;
        while (s != null && !s.isClosed()) {
            try {
                Socket c = s.accept();
                if (!isLan(c.getInetAddress())) {
                    c.close();
                    continue;
                }
                pool.execute(() -> serve(c));
            } catch (IOException | RuntimeException e) {
                if (s.isClosed()) return;
            }
        }
    }

    private void serve(Socket c) {
        try (Socket sock = c) {
            sock.setSoTimeout(5000);
            Request req = read(sock.getInputStream());
            Response res;
            if (req == null) {
                res = Response.error(400, "bad request");
            } else {
                try {
                    res = handler.handle(req);
                } catch (RuntimeException e) {
                    res = Response.error(500, "error");
                }
            }
            OutputStream out = sock.getOutputStream();
            String head = "HTTP/1.1 " + res.code + " " + (res.code == 200 ? "OK" : "Error") + "\r\n"
                    + "Content-Type: " + res.type + "\r\n"
                    + "Content-Length: " + res.body.length + "\r\n"
                    + "Cache-Control: no-store\r\n"
                    + "X-Content-Type-Options: nosniff\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.UTF_8));
            out.write(res.body);
            out.flush();
        } catch (IOException ignored) {
        }
    }

    // ---- HTTP ------------------------------------------------------------------------------------

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 8192) return null;
        }
        if (c == -1 && b.size() == 0) return null;
        return new String(b.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    /** Lê um pedido HTTP/1.x; null se estiver malformado ou grande demais. */
    static Request read(InputStream in) throws IOException {
        String first = line(in);
        if (first == null) return null;
        String[] parts = first.split(" ");
        if (parts.length < 2) return null;
        Map<String, String> headers = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            String h = line(in);
            if (h == null) return null;
            if (h.isEmpty()) break;
            int colon = h.indexOf(':');
            if (colon > 0) headers.put(h.substring(0, colon).trim().toLowerCase(), h.substring(colon + 1).trim());
        }
        int length = 0;
        try {
            length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
        } catch (NumberFormatException e) {
            return null;
        }
        if (length < 0 || length > MAX_BODY) return null;
        byte[] body = new byte[length];
        int n = 0;
        while (n < length) {
            int r = in.read(body, n, length - n);
            if (r < 0) return null;
            n += r;
        }
        String target = parts[1];
        String path = target;
        Map<String, String> query = new HashMap<>();
        int q = target.indexOf('?');
        if (q >= 0) {
            path = target.substring(0, q);
            for (String kv : target.substring(q + 1).split("&")) {
                int eq = kv.indexOf('=');
                String k = eq < 0 ? kv : kv.substring(0, eq);
                String v = eq < 0 ? "" : kv.substring(eq + 1);
                try {
                    query.put(URLDecoder.decode(k, "UTF-8"), URLDecoder.decode(v, "UTF-8"));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return new Request(parts[0].toUpperCase(), path, query, headers, body);
    }

    // ---- rede ------------------------------------------------------------------------------------

    static boolean isLan(InetAddress a) {
        if (a == null) return false;
        if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress()) return true;
        byte[] b = a.getAddress();
        return b.length == 16 && (b[0] & 0xFE) == 0xFC;   // IPv6 local único fc00::/7
    }

    /** Primeiro endereço IPv4 privado desta TV (Wi-Fi ou cabo); null sem rede. */
    static String localIp() {
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            if (all == null) return null;
            for (NetworkInterface ni : Collections.list(all)) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }
}
