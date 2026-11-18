package com.musiclisten.app

/** Tunable always-on timing. Kept in one place so unit tests can lock the contract. */
object ListenTiming {
    const val PROBE_MS = 900L
    const val PROBE_GAP_MS = 900L
    const val CAPTURE_MS = 7_000L
    const val REQUIRED_MUSIC_STREAK = 2

    /** After a successful match before another AudD call. */
    const val COOLDOWN_AFTER_MATCH_MS = 25_000L

    /** After AudD returned no match. */
    const val COOLDOWN_AFTER_MISS_MS = 15_000L

    /** After network/parse/recorder errors. */
    const val COOLDOWN_AFTER_ERROR_MS = 20_000L

    /** Do not re-notify the same title+artist within this window. */
    const val SAME_TRACK_SUPPRESS_MS = 40_000L

    /** When ambient audio goes quiet, shrink remaining cooldown to at most this. */
    const val QUIET_SOFTEN_COOLDOWN_MS = 8_000L
}
