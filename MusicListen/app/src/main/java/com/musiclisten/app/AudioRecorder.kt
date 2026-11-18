package com.musiclisten.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Captures a short PCM snippet and writes it as a WAV file for AudD.
 */
class AudioRecorder {
    fun recordToWav(output: File, durationMs: Long = 7_000L): File {
        val sampleRate = 44_100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = max(minBuf, sampleRate) * 2

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("AudioRecord failed to initialize")
        }

        val pcm = ByteArrayOutputStream()
        val buffer = ByteArray(bufferSize)
        val endAt = System.currentTimeMillis() + durationMs
        recorder.startRecording()
        try {
            while (System.currentTimeMillis() < endAt) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    pcm.write(buffer, 0, read)
                } else if (read < 0) {
                    throw IllegalStateException("AudioRecord read error: $read")
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }

        writeWav(output, pcm.toByteArray(), sampleRate, channels = 1, bitsPerSample = 16)
        return output
    }

    private fun writeWav(
        file: File,
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val totalDataLen = pcmData.size + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * bitsPerSample / 8).toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmData.size)

        FileOutputStream(file).use { out ->
            out.write(header.array())
            out.write(pcmData)
        }
    }
}
