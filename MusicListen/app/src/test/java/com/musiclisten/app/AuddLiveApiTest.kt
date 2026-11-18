package com.musiclisten.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live network check. Runs only when AUDD_API_TOKEN is provided to Gradle/JVM.
 */
class AuddLiveApiTest {
    @Test
    fun recognizesKnownDemoUrl() {
        val token = System.getenv("AUDD_API_TOKEN")
            ?.takeIf { it.isNotBlank() }
            ?: System.getProperty("audd.api.token")
                ?.takeIf { it.isNotBlank() }
        assumeTrue("AUDD_API_TOKEN not set — skipping live API test", !token.isNullOrBlank())

        val client = AuddClient(token!!)
        val track = client.recognizeUrl("https://audd.tech/example.mp3")
        assertNotNull(track)
        assertEquals("Everybody Wants To Rule The World", track!!.title)
        assertEquals("Tears For Fears", track.artist)
    }
}
