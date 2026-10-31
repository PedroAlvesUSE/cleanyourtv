package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SleepTimerTest {
    @Test
    public void stepsCycleOffOneFifteenThirtySixtyNinetyOff() {
        assertEquals(1, SleepTimer.next(0));
        assertEquals(15, SleepTimer.next(1));
        assertEquals(30, SleepTimer.next(15));
        assertEquals(60, SleepTimer.next(30));
        assertEquals(90, SleepTimer.next(60));
        assertEquals(0, SleepTimer.next(90));
        assertEquals(1, SleepTimer.next(45));
    }

    @Test
    public void remainingTimeNeverGoesNegative() {
        assertEquals(0, SleepTimer.remainingMs(0, 5000));
        assertEquals(0, SleepTimer.remainingMs(1000, 5000));
        assertEquals(4000, SleepTimer.remainingMs(9000, 5000));
    }

    @Test
    public void formatShowsMinutesOrHours() {
        assertEquals("<1min", SleepTimer.format(0));
        assertEquals("1min", SleepTimer.format(1));
        assertEquals("1min", SleepTimer.format(60_000));
        assertEquals("2min", SleepTimer.format(60_001));
        assertEquals("42min", SleepTimer.format(42 * 60_000L));
        assertEquals("1h 00min", SleepTimer.format(60 * 60_000L));
        assertEquals("1h 30min", SleepTimer.format(90 * 60_000L));
    }
}
