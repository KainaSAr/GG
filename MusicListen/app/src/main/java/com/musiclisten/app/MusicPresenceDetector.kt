package com.musiclisten.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Decides whether a short mic probe is music worth sending to AudD.
 *
 * Tuned on real clips at party loudness: stadium roar, bar crowd, applause,
 * group talk and theatre cheering stay below the line. Full mixes, distorted
 * guitar and music mixed under a louder crowd still pass. The split is real
 * spectral flatness plus bass share, not a loudness check — shouting is loud
 * and still not music.
 */
class MusicPresenceDetector(
    private val sampleRate: Int = AudioMonitor.SAMPLE_RATE,
) {
    data class Result(
        val isMusicLikely: Boolean,
        val score: Double,
        val rms: Double,
        val reason: String,
    )

    fun analyze(pcm: ShortArray): Result {
        if (pcm.size < sampleRate / 4) {
            return Result(false, 0.0, 0.0, "too_short")
        }
        var sumSq = 0.0
        var i = 0
        while (i < pcm.size) {
            val s = pcm[i].toDouble()
            sumSq += s * s
            i++
        }
        val rms = sqrt(sumSq / pcm.size) / Short.MAX_VALUE
        if (rms < SILENCE_RMS) {
            return Result(false, 0.0, rms, "silence")
        }

        val flats = ArrayList<Double>()
        val lows = ArrayList<Double>()
        val peaks = ArrayList<Double>()
        val frame = DoubleArray(FRAME)
        var offset = 0
        while (offset + FRAME <= pcm.size) {
            var n = 0
            while (n < FRAME) {
                frame[n] = (pcm[offset + n].toDouble() / Short.MAX_VALUE) * HANN[n]
                n++
            }
            val mag = Fft.magnitude(frame)
            var logSum = 0.0
            var arith = 0.0
            var maxBin = 0.0
            var lowEnergy = 0.0
            var bandEnergy = 0.0
            var bin = 1
            while (bin < mag.size) {
                val value = mag[bin] + 1e-12
                logSum += ln(value)
                arith += value
                if (value > maxBin) maxBin = value
                val hz = bin * sampleRate.toDouble() / FRAME
                if (hz in 40.0..8000.0) {
                    bandEnergy += value
                    if (hz < 180.0) lowEnergy += value
                }
                bin++
            }
            val bins = mag.size - 1
            if (arith > 0.0 && bins > 0) {
                val mean = arith / bins
                flats += exp(logSum / bins) / mean
                peaks += maxBin / mean
                lows += lowEnergy / (bandEnergy + 1e-12)
            }
            offset += HOP
        }
        if (flats.isEmpty()) {
            return Result(false, 0.0, rms, "no_frames")
        }
        val flat = median(flats)
        val low = median(lows)
        val peak = median(peaks)
        val tonal = flat <= DRY_FLAT_MAX && peak >= DRY_PEAK_MIN
        val bassy = low >= BASS_SHARE_MIN && flat <= BASS_FLAT_MAX
        val music = tonal || bassy
        val score = if (music) {
            (0.75 + (BASS_FLAT_MAX - flat)).coerceIn(0.7, 0.98)
        } else {
            (0.45 * (1.0 - flat)).coerceIn(0.0, 0.5)
        }
        val reason = buildString {
            append(if (music) "music" else "noise")
            append(" flat=")
            append("%.3f".format(flat))
            append(" low=")
            append("%.2f".format(low))
            append(" peak=")
            append("%.0f".format(peak))
        }
        return Result(music, score, rms, reason)
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    companion object {
        private const val FRAME = 2048
        private const val HOP = 1024
        private const val SILENCE_RMS = 0.012
        private const val BASS_SHARE_MIN = 0.16
        private const val BASS_FLAT_MAX = 0.12
        private const val DRY_FLAT_MAX = 0.025
        private const val DRY_PEAK_MIN = 22.0

        private val HANN = DoubleArray(FRAME) { i ->
            0.5 * (1.0 - cos(2.0 * PI * i / (FRAME - 1)))
        }
    }
}
