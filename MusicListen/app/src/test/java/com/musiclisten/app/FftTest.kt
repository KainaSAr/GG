package com.musiclisten.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class FftTest {
    @Test
    fun sinePeaksAtItsFrequencyBin() {
        val n = 2048
        val sampleRate = 44_100
        val freq = 110.0
        val frame = DoubleArray(n) { i -> sin(2.0 * PI * freq * i / sampleRate) }
        val mag = Fft.magnitude(frame)
        val peak = mag.indices.maxBy { mag[it] }
        val expected = (freq * n / sampleRate).toInt()
        assertTrue("peak bin $peak, expected near $expected", kotlin.math.abs(peak - expected) <= 1)
        assertTrue(mag[peak] > mag[0] * 5)
    }

    @Test
    fun rejectsNonPowerOfTwo() {
        try {
            Fft.magnitude(DoubleArray(1000))
            throw AssertionError("expected failure")
        } catch (e: IllegalArgumentException) {
            assertEquals(true, e.message?.contains("power of two"))
        }
    }
}
