package com.musiclisten.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Keeps a single AudioRecord session for continuous local probing and
 * occasional longer captures for recognition.
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
        release()
        val created = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize,
        )
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            throw IllegalStateException("AudioRecord failed to initialize")
        }
        created.startRecording()
        recorder = created
    }

    @Synchronized
    fun release() {
        val current = recorder ?: return
        recorder = null
        runCatching { current.stop() }
        runCatching { current.release() }
    }

    fun readPcm(durationMs: Long): ShortArray {
        ensureStarted()
        val rec = recorder ?: throw IllegalStateException("recorder missing")
        val samplesNeeded = ((SAMPLE_RATE * durationMs) / 1000L).toInt().coerceAtLeast(1)
        val out = ShortArray(samplesNeeded)
        var offset = 0
        val endAt = System.currentTimeMillis() + durationMs + 250
        while (offset < samplesNeeded && System.currentTimeMillis() < endAt) {
            val read = rec.read(out, offset, samplesNeeded - offset)
            when {
                read > 0 -> offset += read
                read == AudioRecord.ERROR_INVALID_OPERATION ||
                    read == AudioRecord.ERROR_BAD_VALUE -> {
                    throw IllegalStateException("AudioRecord read error: $read")
                }
            }
        }
        return if (offset == samplesNeeded) out else out.copyOf(offset)
    }

    fun captureWav(file: File, durationMs: Long): File {
        val pcm = readPcm(durationMs)
        writeWav(file, pcm)
        return file
    }

    private fun writeWav(file: File, pcm: ShortArray) {
        val dataSize = pcm.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize + 36)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(1)
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)

        FileOutputStream(file).use { out ->
            out.write(header.array())
            val bytes = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            pcm.forEach { bytes.putShort(it) }
            out.write(bytes.array())
        }
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
