package com.musiclisten.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class MusicPresenceDetectorTest {
    private val sampleRate = 44_100
    private val detector = MusicPresenceDetector(sampleRate)

    @Test
    fun silenceIsNotMusic() {
        val pcm = ShortArray(sampleRate) { 0 }
        val result = detector.analyze(pcm)
        assertFalse(result.isMusicLikely)
        assertTrue(result.reason.contains("silence") || result.score < 0.55)
    }

    @Test
    fun whiteNoiseIsUsuallyNotMusic() {
        val rnd = Random(7)
        val pcm = ShortArray(sampleRate) {
            ((rnd.nextDouble() * 2 - 1) * 12_000).toInt().toShort()
        }
        val result = detector.analyze(pcm)
        assertFalse("noise scored ${result.score} reason=${result.reason}", result.isMusicLikely)
    }

    @Test
    fun sustainedHarmonicToneLooksLikeMusic() {
        val pcm = ShortArray(sampleRate) { i ->
            val t = i.toDouble() / sampleRate
            val sample =
                0.45 * sin(2 * PI * 110 * t) +
                    0.30 * sin(2 * PI * 220 * t) +
                    0.20 * sin(2 * PI * 330 * t) +
                    0.10 * sin(2 * PI * 440 * t)
            (sample * Short.MAX_VALUE * 0.35).toInt().toShort()
        }
        val result = detector.analyze(pcm)
        assertTrue(
            "harmonic tone should pass gate, score=${result.score} reason=${result.reason}",
            result.isMusicLikely,
        )
    }

    @Test
    fun choppyBurstsAreNotMusic() {
        val pcm = ShortArray(sampleRate) { i ->
            val inBurst = (i % (sampleRate / 5)) < sampleRate / 40
            if (inBurst) (18_000 * sin(2 * PI * 1000 * i / sampleRate)).toInt().toShort() else 0
        }
        val result = detector.analyze(pcm)
        assertFalse("choppy speech-like scored ${result.score}", result.isMusicLikely)
    }

    @Test
    fun tooShortRejected() {
        val pcm = ShortArray(1000) { 1000 }
        assertFalse(detector.analyze(pcm).isMusicLikely)
    }
}
