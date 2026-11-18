package com.musiclisten.app

/**
 * Tuned for musical lotto: each song is only 15–30 seconds (blitz can be 5–7).
 * Identify inside that window, then drop the cooldown as soon as the room goes quiet
 * so the next clip is not skipped.
 */
object ListenTiming {
    const val PROBE_MS = 700L
    const val PROBE_GAP_MS = 300L
    const val CAPTURE_MS = 4_500L
    const val REQUIRED_MUSIC_STREAK = 2

    /** A single confident local probe is enough to start identification. */
    const val STRONG_PROBE_SCORE = 0.72

    /** While the same clip is still playing. Quiet gap resets this. */
    const val COOLDOWN_AFTER_MATCH_MS = 12_000L
    const val COOLDOWN_AFTER_MISS_MS = 6_000L
    const val COOLDOWN_AFTER_ERROR_MS = 8_000L

    /** Do not buzz again for the same title while the clip repeats. */
    const val SAME_TRACK_SUPPRESS_MS = 90_000L

    /** Consecutive quiet probes after which the next song may be sent immediately. */
    const val QUIET_RESET_STREAK = 2
}
