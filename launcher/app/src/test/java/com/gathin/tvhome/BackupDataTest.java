package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class BackupDataTest {
    private static void rejects(String json) {
        try {
            BackupData.parse(json);
            fail("accepted: " + json);
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void exportThenParseRoundTrips() throws Exception {
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("dock_glow", true);
        prefs.put("columns", 5);
        prefs.put("wallpaper_choice", "bing");
        prefs.put("opacity", 0.5f);
        prefs.put("big", 123456789012L);
        Set<String> apps = new HashSet<>();
        apps.add("a.b");
        prefs.put("parental_apps", apps);
        BackupData.Parsed back = BackupData.parse(BackupData.export("{\"columns\":4}", prefs));
        assertEquals("{\"columns\":4}", back.config);
        assertEquals(Boolean.TRUE, back.settings.get("dock_glow"));
        assertEquals(5, back.settings.get("columns"));
        assertEquals("bing", back.settings.get("wallpaper_choice"));
        assertEquals(123456789012L, back.settings.get("big"));
        assertEquals(apps, back.settings.get("parental_apps"));
        assertTrue(back.settings.get("opacity") instanceof Float);
    }

    @Test
    public void pinsAndTimerNeverLeaveTheTv() throws Exception {
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put(Parental.PIN_KEY, "salt:hash");
        prefs.put(RemoteServer.PIN_KEY, "1234");
        prefs.put(SleepTimer.DEADLINE_KEY, 5L);
        prefs.put("dock_glow", true);
        JSONObject out = new JSONObject(BackupData.export(null, prefs)).getJSONObject("settings");
        assertEquals(1, out.length());
        assertTrue(out.has("dock_glow"));
    }

    @Test
    public void importIgnoresSecretsEvenIfTheFileCarriesThem() {
        BackupData.Parsed p = BackupData.parse("{\"app\":\"cleanyourtv\",\"version\":1,\"settings\":"
                + "{\"parental_pin\":\"x:y\",\"phone_remote_pin\":\"0000\",\"dock_glow\":false}}");
        assertFalse(p.settings.containsKey("parental_pin"));
        assertFalse(p.settings.containsKey("phone_remote_pin"));
        assertEquals(Boolean.FALSE, p.settings.get("dock_glow"));
        assertNull(p.config);
    }

    @Test
    public void importRejectsWhatIsNotABackup() {
        rejects("");
        rejects("not json");
        rejects("[]");
        rejects("{\"app\":\"other\",\"version\":1,\"settings\":{}}");
        rejects("{\"app\":\"cleanyourtv\",\"version\":2,\"settings\":{}}");
        rejects("{\"app\":\"cleanyourtv\",\"version\":1}");
        rejects("{\"app\":\"cleanyourtv\",\"version\":1,\"settings\":[]}");
        rejects("{\"app\":\"cleanyourtv\",\"version\":1,\"settings\":{},\"config\":\"text\"}");
    }

    @Test
    public void importRejectsBadKeysAndValues() {
        String head = "{\"app\":\"cleanyourtv\",\"version\":1,\"settings\":";
        rejects(head + "{\"bad key!\":1}}");
        rejects(head + "{\"../x\":1}}");
        rejects(head + "{\"k\":{\"a\":1}}}");
        rejects(head + "{\"k\":null}}");
        rejects(head + "{\"k\":[1,2]}}");
    }

    @Test
    public void importRejectsHugeFiles() {
        StringBuilder sb = new StringBuilder("{\"app\":\"cleanyourtv\",\"version\":1,\"settings\":{\"k\":\"");
        for (int i = 0; i < BackupData.MAX_BYTES; i++) sb.append('a');
        rejects(sb.append("\"}}").toString());
    }
}
