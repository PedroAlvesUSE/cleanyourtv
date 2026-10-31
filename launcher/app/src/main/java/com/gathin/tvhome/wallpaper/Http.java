package com.gathin.tvhome.wallpaper;

import java.util.Map;

/** Thin text GET, so the providers can be tested with canned answers. */
public interface Http {
    /**
     * GET {@code url} and return the body as text.
     *
     * @param headers extra request headers; may be null. They replace the default ones.
     * @throws ProviderException on timeout, any non-2xx answer (429 and 5xx included) or a body over the size limit
     */
    String getText(String url, Map<String, String> headers) throws ProviderException;
}
