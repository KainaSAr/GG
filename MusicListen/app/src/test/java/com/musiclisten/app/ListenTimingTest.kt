package com.musiclisten.app

import org.junit.Assert.assertTrue
import org.junit.Test

class ListenTimingTest {
    @Test
    fun cooldownsStayShortForAlwaysOn() {
        assertTrue(ListenTiming.COOLDOWN_AFTER_MATCH_MS <= 30_000L)
        assertTrue(ListenTiming.COOLDOWN_AFTER_MISS_MS <= 20_000L)
        assertTrue(ListenTiming.COOLDOWN_AFTER_ERROR_MS <= 30_000L)
        assertTrue(ListenTiming.SAME_TRACK_SUPPRESS_MS <= 60_000L)
        assertTrue(ListenTiming.QUIET_SOFTEN_COOLDOWN_MS < ListenTiming.COOLDOWN_AFTER_MATCH_MS)
    }

    @Test
    fun captureLongEnoughForFingerprints() {
        assertTrue(ListenTiming.CAPTURE_MS in 5_000L..12_000L)
        assertTrue(ListenTiming.REQUIRED_MUSIC_STREAK >= 1)
    }
}
