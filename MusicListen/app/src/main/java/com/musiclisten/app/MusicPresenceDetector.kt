package com.musiclisten.app

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Local, offline gate: decide whether ambient audio looks like sustained music
 * before spending an AudD API call.
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
        var peak = 0
        var zeroCrossings = 0
        var prev = 0
        for (i in pcm.indices) {
            val s = pcm[i].toInt()
            sumSq += s.toDouble() * s
            peak = max(peak, abs(s))
            if (i > 0 && ((prev >= 0 && s < 0) || (prev < 0 && s >= 0))) {
                zeroCrossings++
            }
            prev = s
        }
        val rms = sqrt(sumSq / pcm.size) / Short.MAX_VALUE
        val peakNorm = peak.toDouble() / Short.MAX_VALUE
        val durationSec = pcm.size.toDouble() / sampleRate
        val zcr = zeroCrossings / durationSec // crossings per second

        // Frame-level sustain: music stays loud; speech/noise often gaps more.
        val frame = max(256, sampleRate / 50) // ~20 ms
        var loudFrames = 0
        var totalFrames = 0
        var i = 0
        while (i + frame <= pcm.size) {
            var e = 0.0
            for (j in 0 until frame) {
                val v = pcm[i + j].toDouble()
                e += v * v
            }
            val frameRms = sqrt(e / frame) / Short.MAX_VALUE
            if (frameRms > 0.018) loudFrames++
            totalFrames++
            i += frame
        }
        val duty = if (totalFrames == 0) 0.0 else loudFrames.toDouble() / totalFrames

        // Rough band energy via simple comb averaging (no FFT dependency).
        val low = bandEnergy(pcm, 90.0, 350.0)
        val mid = bandEnergy(pcm, 350.0, 2800.0)
        val high = bandEnergy(pcm, 2800.0, 8000.0)
        val totalBand = low + mid + high + 1e-9
        val lowRatio = low / totalBand
        val midRatio = mid / totalBand
        val highRatio = high / totalBand

        // Spectral flatness proxy: music has tonal peaks → lower flatness than hiss.
        val flatness = spectralFlatnessProxy(pcm)

        var score = 0.0
        val reasons = mutableListOf<String>()

        if (rms < 0.012 || peakNorm < 0.04) {
            return Result(false, 0.0, rms, "silence")
        }
        reasons += "loud"

        // Sustained presence
        if (duty >= 0.55) {
            score += 0.35
            reasons += "sustained"
        } else if (duty >= 0.35) {
            score += 0.15
        } else {
            return Result(false, score, rms, "choppy")
        }

        // Music usually has low (bass) and/or mid harmonic content.
        if (lowRatio in 0.05..0.70 && midRatio in 0.15..0.80) {
            score += 0.30
            reasons += "bands"
        } else if (lowRatio + midRatio > 0.55) {
            score += 0.18
            reasons += "bands_low"
        } else if (midRatio > 0.45) {
            score += 0.12
        }

        // ZCR: tonal music often sits low (~100–800). Hiss/noise is very high.
        when {
            zcr in 80.0..4200.0 -> {
                score += 0.20
                reasons += "zcr"
            }
            zcr in 4200.0..7000.0 -> score += 0.05
            zcr > 7000.0 -> {
                score -= 0.25
                reasons += "zcr_noise"
            }
        }

        // Prefer non-hiss textures.
        if (flatness < 0.55) {
            score += 0.15
            reasons += "tonal"
        } else if (flatness > 0.8 && highRatio > 0.45) {
            score -= 0.20
            reasons += "hiss"
        }

        val isMusic = score >= 0.55
        return Result(isMusic, score.coerceIn(0.0, 1.0), rms, reasons.joinToString(","))
    }

    private fun bandEnergy(pcm: ShortArray, fLow: Double, fHigh: Double): Double {
        // One-pole band emphasis via difference of two crude lowpasses.
        val aLow = expAlpha(fLow)
        val aHigh = expAlpha(fHigh)
        var lpLow = 0.0
        var lpHigh = 0.0
        var energy = 0.0
        for (s in pcm) {
            val x = s.toDouble()
            lpLow += aLow * (x - lpLow)
            lpHigh += aHigh * (x - lpHigh)
            val band = lpHigh - lpLow
            energy += band * band
        }
        return energy / pcm.size
    }

    private fun expAlpha(cutoffHz: Double): Double {
        val c = min(cutoffHz, sampleRate / 2.0 - 1.0)
        return 1.0 - kotlin.math.exp(-2.0 * Math.PI * c / sampleRate)
    }

    private fun spectralFlatnessProxy(pcm: ShortArray): Double {
        // Geometric/arithmetic mean of a few sub-band energies ≈ flatness.
        val bands = listOf(
            bandEnergy(pcm, 80.0, 200.0),
            bandEnergy(pcm, 200.0, 600.0),
            bandEnergy(pcm, 600.0, 1600.0),
            bandEnergy(pcm, 1600.0, 4000.0),
            bandEnergy(pcm, 4000.0, 9000.0),
        ).map { max(it, 1e-12) }
        val logMean = bands.sumOf { ln(it) } / bands.size
        val geo = kotlin.math.exp(logMean)
        val arith = bands.sum() / bands.size
        return (geo / arith).coerceIn(0.0, 1.0)
    }
}
