package com.gathin.tvhome.wallpaper;

/** A source could not give a picture today: timeout, HTTP 429/5xx, bad JSON, a video, no image or no key. */
public final class ProviderException extends Exception {
    public ProviderException(String message) {
        super(message);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
