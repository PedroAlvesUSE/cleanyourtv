package com.gathin.tvhome.wallpaper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** {@link Http} on HttpURLConnection: 8 s connect and read timeouts, 2 MB of text at most, our own User-Agent. */
public final class UrlConnectionHttp implements Http {
    public static final String USER_AGENT = "CleanYourTV/2.4 (Android TV launcher)";
    static final int TIMEOUT_MS = 8000;
    static final int MAX_BYTES = 2 * 1024 * 1024;

    @Override
    public String getText(String url, Map<String, String> headers) throws ProviderException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }
            int code = connection.getResponseCode();
            if (code == 429) throw new ProviderException("HTTP 429 (rate limited)");
            if (code >= 500) throw new ProviderException("HTTP " + code + " (server error)");
            if (code < 200 || code >= 300) throw new ProviderException("HTTP " + code);
            return read(connection.getInputStream());
        } catch (IOException e) {
            throw new ProviderException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException, ProviderException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = stream.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                if (out.size() > MAX_BYTES) throw new ProviderException("answer over " + MAX_BYTES + " bytes");
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
