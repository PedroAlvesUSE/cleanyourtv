package com.gathin.tvhome.wallpaper;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class DailySeedTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 30);

    @Test public void sameDateGivesSameIndex() {
        assertEquals(DailySeed.index(DAY, "chromecast", 1000), DailySeed.index(LocalDate.of(2026, 10, 30), "chromecast", 1000));
    }

    @Test public void differentDatesVary() {
        Set<Integer> seen = new HashSet<>();
        for (int d = 1; d <= 30; d++) seen.add(DailySeed.index(LocalDate.of(2026, 11, d), null, 1000));
        assertTrue("30 days gave only " + seen.size() + " different indexes", seen.size() >= 25);
    }

    @Test public void indexStaysInRange() {
        for (int d = 0; d < 400; d++) {
            int i = DailySeed.index(DAY.plusDays(d), "x", 7);
            assertTrue(i >= 0 && i < 7);
        }
        assertEquals(0, DailySeed.index(DAY, null, 1));
    }

    @Test public void saltChangesTheChoice() {
        assertNotEquals(DailySeed.value(DAY, "chromecast"), DailySeed.value(DAY, "earthview"));
        assertEquals(DailySeed.value(DAY, null), DailySeed.value(DAY, ""));
    }

    @Test public void valueIsNeverNegative() {
        for (int d = 0; d < 400; d++) assertTrue(DailySeed.value(DAY.plusDays(d), "s") >= 0);
    }

    @Test(expected = IllegalArgumentException.class) public void emptyRangeIsRefused() {
        DailySeed.index(DAY, null, 0);
    }

    @Test public void shuffleKeepsTheItemsAndIsStableForTheDay() {
        List<String> items = Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h");
        List<String> one = DailySeed.shuffle(items, DAY, "all_random");
        assertEquals(one, DailySeed.shuffle(items, DAY, "all_random"));
        assertEquals(new HashSet<>(items), new HashSet<>(one));
        assertEquals(items.size(), one.size());
    }

    @Test public void shuffleChangesWithTheDate() {
        List<String> items = Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h");
        Set<List<String>> orders = new HashSet<>();
        for (int d = 0; d < 10; d++) orders.add(DailySeed.shuffle(items, DAY.plusDays(d), "all_random"));
        assertTrue(orders.size() > 5);
    }
}
