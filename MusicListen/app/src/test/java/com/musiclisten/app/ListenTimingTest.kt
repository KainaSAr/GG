package com.musiclisten.app

import org.junit.Assert.assertTrue
import org.junit.Test

class ListenTimingTest {
    @Test
    fun fitsInsideALottoClip() {
        // Detection plus capture must finish inside a typical 15–30s clip.
        val worstStartMs = ListenTiming.PROBE_MS + ListenTiming.PROBE_GAP_MS
        assertTrue(worstStartMs + ListenTiming.CAPTURE_MS <= 15_000L)
        assertTrue(ListenTiming.CAPTURE_MS in 4_000L..8_000L)
    }

    @Test
    fun quietGapClearsCooldownBeforeNextSong() {
        assertTrue(ListenTiming.COOLDOWN_AFTER_MATCH_MS <= 15_000L)
        assertTrue(ListenTiming.COOLDOWN_AFTER_MISS_MS <= 8_000L)
        assertTrue(ListenTiming.QUIET_RESET_STREAK in 1..3)
        assertTrue(ListenTiming.STRONG_PROBE_SCORE in 0.6..0.9)
        assertTrue(ListenTiming.REQUIRED_MUSIC_STREAK >= 1)
    }
}
