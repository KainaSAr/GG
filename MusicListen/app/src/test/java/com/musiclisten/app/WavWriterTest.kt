package com.musiclisten.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavWriterTest {
    @Test
    fun headerIsPcm16Mono() {
        val dataBytes = 44100 * 2
        val header = WavWriter.header(dataBytes, 44_100)
        assertEquals(44, header.size)
        assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(header, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(header, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(header, 36, 4, Charsets.US_ASCII))

        val view = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(dataBytes + 36, view.getInt(4))
        assertEquals(16, view.getInt(16))
        assertEquals(1.toShort(), view.getShort(20))
        assertEquals(1.toShort(), view.getShort(22))
        assertEquals(44_100, view.getInt(24))
        assertEquals(88_200, view.getInt(28))
        assertEquals(2.toShort(), view.getShort(32))
        assertEquals(16.toShort(), view.getShort(34))
        assertEquals(dataBytes, view.getInt(40))
    }
}
