package com.musiclisten.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class RecognitionChainTest {
    @Test
    fun quotaFallsThroughToTheNextProvider() {
        val file = File("clip.wav")
        val used = mutableListOf<String>()
        val chain = RecognitionChain(
            listOf(
                fake("AudD") {
                    used += "AudD"
                    throw ProviderQuotaException("AudD", "quota")
                },
                fake("Song Finder") {
                    used += "Song Finder"
                    TrackResult("Happy Rock", "Bensound")
                },
            ),
        )
        val found = chain.recognize(file)
        assertEquals(listOf("AudD", "Song Finder"), used)
        assertEquals("Song Finder", found.provider)
        assertEquals("Happy Rock", found.track!!.title)
    }

    @Test
    fun noMatchDoesNotSpendTheNextProvider() {
        val used = mutableListOf<String>()
        val chain = RecognitionChain(
            listOf(
                fake("AudD") {
                    used += "AudD"
                    null
                },
                fake("Song Finder") {
                    used += "Song Finder"
                    TrackResult("Other", "X")
                },
            ),
        )
        val found = chain.recognize(File("clip.wav"))
        assertNull(found.track)
        assertEquals(listOf("AudD"), used)
    }

    private fun fake(name: String, body: () -> TrackResult?) = object : SongSource {
        override val id = name
        override fun recognize(file: File) = body()
    }
}
