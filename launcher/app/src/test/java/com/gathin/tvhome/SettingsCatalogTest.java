package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhome.SettingsCatalog.SettingRow;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsCatalogTest {

    private static void collect(SettingRow row, List<SettingRow> out) {
        out.add(row);
        if (row.children != null) for (SettingRow child : row.children) collect(child, out);
    }

    private static List<SettingRow> all() {
        List<SettingRow> out = new ArrayList<>();
        collect(SettingsCatalog.ROOT, out);
        return out;
    }

    @Test
    public void everyFeatureHasExactlyOneSwitchAndEverySwitchIsAFeature() {
        Set<String> switches = new HashSet<>();
        for (SettingRow row : all()) {
            if (row.type != SettingsCatalog.BOOL) continue;
            assertTrue("duplicate switch " + row.key, switches.add(row.key));
            assertTrue("switch without a Config feature: " + row.key, Config.FEATURES.containsKey(row.key));
        }
        assertEquals(Config.FEATURES.keySet(), switches);
    }

    @Test
    public void keysAreUniqueAndScreensHaveRows() {
        Set<String> keys = new HashSet<>();
        for (SettingRow row : all()) {
            if (row.type == SettingsCatalog.SUBSCREEN) {
                assertNull(row.key);
                assertTrue(row.children.length > 0);
            } else {
                assertTrue("duplicate key " + row.key, keys.add(row.key));
                assertEquals(0, row.children.length);
            }
            assertNotEquals(0, row.title);
            assertNotEquals(0, row.description);
        }
    }

    @Test
    public void rootListsTheNineSections() {
        assertEquals(9, SettingsCatalog.ROOT.children.length);
        for (SettingRow section : SettingsCatalog.ROOT.children) {
            assertEquals(SettingsCatalog.SUBSCREEN, section.type);
            assertNotEquals(0, section.icon);
        }
    }

    @Test
    public void onlyUnbuiltFunctionsAreMarkedSoon() {
        for (String built : new String[]{Config.FEATURE_DOCK_GLOW, Config.FEATURE_SKY, Config.DOCK_GLASS_KEY,
                Config.AMBIENT_TINT_KEY, Config.FEATURE_MODULE_CARDS, Config.FEATURE_FILL_DOCK,
                Config.FEATURE_APP_KEYS, Config.FEATURE_BING, Config.FEATURE_ANIMATIONS,
                Config.FEATURE_ADVANCED_EFFECTS,
                "wallpaper_sources", "wallpaper_credit", "wallpaper_diag", "seasonal_wallpaper",
                Apple.STYLE, Apple.PARALLAX, Apple.TOP_SHELF, Apple.AUTO_ACCENT}) {
            for (SettingRow row : all()) if (built.equals(row.key)) assertFalse(built, row.soon);
        }
    }

    @Test
    public void newFunctionsAreOffByDefaultExceptTheAppleLook() {
        assertFalse(Config.FEATURES.get(Config.FEATURE_DOCK_GLOW));
        assertTrue(Config.FEATURES.get("apple_style"));
        assertTrue(Config.FEATURES.get("parallax"));
        for (String key : new String[]{"wallpaper_sources", "continue_watching", "mic_tizentube", "profiles",
                "night_mode", "quick_clean", "own_screensaver"}) {
            assertFalse(key, Config.FEATURES.get(key));
        }
        // The functions the launcher already had keep their old defaults.
        assertTrue(Config.FEATURES.get(Config.AMBIENT_TINT_KEY));
        assertFalse(Config.FEATURES.get(Config.DOCK_GLASS_KEY));
        assertTrue(Config.FEATURES.get(Config.FEATURE_BING));
    }

    @Test
    public void tvChoiceBeatsThemeAndThemeBeatsDefault() {
        assertFalse(Config.resolveFeature(false, null, null));
        assertTrue(Config.resolveFeature(true, null, null));
        assertTrue(Config.resolveFeature(false, Boolean.TRUE, null));
        assertFalse(Config.resolveFeature(true, Boolean.FALSE, null));
        assertFalse(Config.resolveFeature(true, Boolean.TRUE, Boolean.FALSE));
        assertTrue(Config.resolveFeature(false, Boolean.FALSE, Boolean.TRUE));
    }

    @Test
    public void valuesThatAreNotBooleansAreIgnored() {
        // A theme's app_keys is a list, not the switch: the default stands.
        assertTrue(Config.resolveFeature(true, "[\"190:com.example\"]", null));
        assertFalse(Config.resolveFeature(false, 1, null));
        assertTrue(Config.resolveFeature(false, Boolean.TRUE, "false"));
        assertFalse(Config.resolveFeature(false, null, 0));
    }
}
