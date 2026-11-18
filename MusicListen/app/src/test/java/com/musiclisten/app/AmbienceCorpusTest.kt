package com.musiclisten.app

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real recordings, loudness-matched to a noisy room.
 * Skipped when the local corpus is not on disk.
 */
class AmbienceCorpusTest {
    private val detector = MusicPresenceDetector(44_100)

    @Test
    fun partyNoiseDoesNotLookLikeMusicAndSongsStillDo() {
        val dir = File("/tmp/lotto-audio/pcm")
        assumeTrue("corpus not downloaded", dir.isDirectory && dir.listFiles()?.isNotEmpty() == true)

        val rates = dir.listFiles { f -> f.extension == "wav" }!!
            .associate { file -> file.nameWithoutExtension to hitRate(loadNormalized(file)) }

        fun rate(name: String) = rates[name] ?: error("missing $name")

        val noise = listOf("applause", "clap", "crowd", "stadium", "talk", "theater")
        noise.forEach { name ->
            assertTrue("$name triggered ${rate(name)}", rate(name) <= 0.08)
        }
        assertTrue("mall ${rate("mall")}", rate("mall") <= 0.12)

        listOf("bensound", "guitar", "rockjam", "mix_equal", "mix_music_loud", "mix_jam_talk").forEach { name ->
            assertTrue("$name only ${rate(name)}", rate(name) >= 0.85)
        }
        assertTrue("music under louder crowd ${rate("mix_crowd_loud")}", rate("mix_crowd_loud") >= 0.50)
    }

    private fun hitRate(pcm: ShortArray): Double {
        val window = (44_100 * 0.9).toInt()
        val hop = (44_100 * 0.45).toInt()
        var hits = 0
        var total = 0
        var offset = 0
        while (offset + window <= pcm.size) {
            if (detector.analyze(pcm.copyOfRange(offset, offset + window)).isMusicLikely) hits++
            total++
            offset += hop
        }
        return if (total == 0) 0.0 else hits.toDouble() / total
    }

    private fun loadNormalized(file: File): ShortArray {
        val bytes = file.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.getInt(0) == 0x46464952) { "not RIFF ${file.name}" }
        val channels = buf.getShort(22).toInt()
        val rate = buf.getInt(24)
        val bits = buf.getShort(34).toInt()
        require(channels == 1 && rate == 44_100 && bits == 16) { file.name }
        var pos = 12
        var dataStart = -1
        var dataSize = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = buf.getInt(pos + 4)
            if (id == "data") {
                dataStart = pos + 8
                dataSize = size
                break
            }
            pos += 8 + size
        }
        require(dataStart > 0)
        val samples = ShortArray(dataSize / 2)
        val data = ByteBuffer.wrap(bytes, dataStart, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in samples.indices) samples[i] = data.short
        var sumSq = 0.0
        for (s in samples) sumSq += s.toDouble() * s
        val rms = kotlin.math.sqrt(sumSq / samples.size) / Short.MAX_VALUE
        val target = 0.18
        if (rms >= target || rms < 1e-6) return samples
        val gain = target / rms
        for (i in samples.indices) {
            val scaled = samples[i] * gain
            samples[i] = scaled.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }
}
