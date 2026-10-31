package com.gathin.tvhome.wallpaper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Canned answers by URL prefix, plus a record of what was asked. Test helper. */
final class FakeHttp implements Http {
    private final Map<String, String> routes = new LinkedHashMap<>();
    final List<String> requests = new ArrayList<>();
    final List<Map<String, String>> requestHeaders = new ArrayList<>();

    /** Answer {@code body} to every URL that starts with {@code prefix}. */
    FakeHttp on(String prefix, String body) {
        routes.put(prefix, body);
        return this;
    }

    /** Answer the sample file {@code wallpaper/<name>.json}. */
    FakeHttp onSample(String prefix, String name) {
        return on(prefix, sample(name));
    }

    @Override
    public String getText(String url, Map<String, String> headers) throws ProviderException {
        requests.add(url);
        requestHeaders.add(headers);
        for (Map.Entry<String, String> route : routes.entrySet()) {
            if (url.startsWith(route.getKey())) return route.getValue();
        }
        throw new ProviderException("no fake answer for " + url);
    }

    static String sample(String name) {
        try (InputStream in = FakeHttp.class.getResourceAsStream("/wallpaper/" + name + ".json")) {
            if (in == null) throw new IllegalStateException("missing sample " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
