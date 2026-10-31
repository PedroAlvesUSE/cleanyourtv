package com.gathin.tvhome.wallpaper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small helpers for the providers. Android's org.json turns a JSON null into the text "null"; these do not. */
final class Json {
    private Json() { }

    static JSONObject object(String text) throws ProviderException {
        try {
            return new JSONObject(text);
        } catch (JSONException e) {
            throw new ProviderException("invalid JSON: " + e.getMessage(), e);
        }
    }

    static JSONArray array(String text) throws ProviderException {
        try {
            return new JSONArray(text);
        } catch (JSONException e) {
            throw new ProviderException("invalid JSON: " + e.getMessage(), e);
        }
    }

    /** The string at {@code key}, trimmed, or "" when missing, null or not a string. */
    static String str(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return "";
        return o.optString(key, "").trim();
    }

    static JSONObject obj(JSONObject o, String key) {
        return o == null ? null : o.optJSONObject(key);
    }

    static JSONArray arr(JSONObject o, String key) {
        return o == null ? null : o.optJSONArray(key);
    }

    /** First element of an array as an object, or null. */
    static JSONObject first(JSONArray a) {
        return a == null || a.length() == 0 ? null : a.optJSONObject(0);
    }

    /** Collapses white space and cuts the text to {@code max} characters with an ellipsis. */
    static String clean(String text, int max) {
        if (text == null) return "";
        String s = text.replaceAll("\\s+", " ").trim();
        return s.length() <= max ? s : s.substring(0, max - 1).trim() + "…";
    }

    static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");   // the Charset overload needs API 33
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);   // UTF-8 always exists
        }
    }

    static Map<String, String> headers(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return Collections.unmodifiableMap(map);
    }
}
