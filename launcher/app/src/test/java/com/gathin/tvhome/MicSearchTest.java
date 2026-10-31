package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.junit.Test;

public class MicSearchTest {
    @Test
    public void defaultsAreAssistVoiceAssistAndSearch() {
        assertArrayEquals(new int[]{219, 231, 84}, MicSearch.DEFAULT_KEYCODES);
    }

    @Test
    public void keycodesComeFromAJsonArray() throws Exception {
        assertArrayEquals(new int[]{219, 4100}, MicSearch.keycodes(new JSONArray("[219, 4100]")));
    }

    @Test
    public void anythingInvalidFallsBackToTheDefaults() throws Exception {
        assertArrayEquals(MicSearch.DEFAULT_KEYCODES, MicSearch.keycodes(null));
        assertArrayEquals(MicSearch.DEFAULT_KEYCODES, MicSearch.keycodes("219"));
        assertArrayEquals(MicSearch.DEFAULT_KEYCODES, MicSearch.keycodes(new JSONArray("[]")));
        assertArrayEquals(MicSearch.DEFAULT_KEYCODES, MicSearch.keycodes(new JSONArray("[219, \"x\"]")));
        assertArrayEquals(MicSearch.DEFAULT_KEYCODES, MicSearch.keycodes(new JSONArray("[219, 0]")));
        assertArrayEquals(MicSearch.DEFAULT_KEYCODES, MicSearch.keycodes(new JSONArray("[-5]")));
    }

    @Test
    public void containsFindsACode() {
        assertTrue(MicSearch.contains(MicSearch.DEFAULT_KEYCODES, 219));
        assertFalse(MicSearch.contains(MicSearch.DEFAULT_KEYCODES, 4));
    }

    @Test
    public void searchUrlEncodesTheTerm() {
        assertEquals("https://www.youtube.com/results?search_query=gatinhos+fofos",
                MicSearch.searchUrl("  gatinhos   fofos "));
        assertEquals("https://www.youtube.com/results?search_query=a%C3%A7%C3%A3o+%26+drama",
                MicSearch.searchUrl("ação & drama"));
    }

    @Test
    public void searchUrlIsEmptyWithoutATerm() {
        assertEquals("", MicSearch.searchUrl(null));
        assertEquals("", MicSearch.searchUrl("   "));
    }
}
