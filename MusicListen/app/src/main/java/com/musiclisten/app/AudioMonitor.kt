package com.musiclisten.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import kotlin.math.max

/**
 * Keeps one microphone session for short local probes and recognition captures.
 * All access is serialized so a stop cannot release the recorder mid-read.
 */
class AudioMonitor {
    private var recorder: AudioRecord? = null
    private val bufferSize: Int

    init {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        bufferSize = max(minBuf, SAMPLE_RATE) * 2
    }

    @Synchronized
    fun ensureStarted() {
        val current = recorder
        if (current != null && current.state == AudioRecord.STATE_INITIALIZED) {
            if (current.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                current.startRecording()
            }
            return
        }
        releaseLocked()
        val created = openRecorder()
        created.startRecording()
        recorder = created
    }

    @Synchronized
    fun release() {
        releaseLocked()
    }

    @Synchronized
    fun readPcm(durationMs: Long): ShortArray {
        ensureStarted()
        val rec = recorder ?: throw IllegalStateException("Микрофон не запущен")
        val samplesNeeded = ((SAMPLE_RATE * durationMs) / 1000L).toInt().coerceAtLeast(1)
        val out = ShortArray(samplesNeeded)
        var offset = 0
        val endAt = System.currentTimeMillis() + durationMs + 400
        while (offset < samplesNeeded && System.currentTimeMillis() < endAt) {
            val read = rec.read(out, offset, samplesNeeded - offset)
            when {
                read > 0 -> offset += read
                read < 0 -> throw IllegalStateException("Сбой микрофона: $read")
            }
        }
        if (offset < samplesNeeded * 8 / 10) {
            throw IllegalStateException("Слишком короткая запись: $offset/$samplesNeeded")
        }
        return if (offset == samplesNeeded) out else out.copyOf(offset)
    }

    @Synchronized
    fun captureWav(file: File, durationMs: Long): File {
        val pcm = readPcm(durationMs)
        if (pcm.size < SAMPLE_RATE) {
            throw IllegalStateException("Запись короче секунды, в API не отправляю")
        }
        WavWriter.write(file, pcm, SAMPLE_RATE)
        return file
    }

    private fun openRecorder(): AudioRecord {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
        for (source in sources) {
            val created = AudioRecord(source, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize)
            if (created.state == AudioRecord.STATE_INITIALIZED) return created
            created.release()
        }
        throw IllegalStateException("Микрофон не запустился")
    }

    private fun releaseLocked() {
        val current = recorder ?: return
        recorder = null
        runCatching { current.stop() }
        runCatching { current.release() }
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
