package com.musiclisten.app

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

class AuddClientMockServerTest {
    private lateinit var server: MockWebServer
    private lateinit var tmp: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tmp = File.createTempFile("probe", ".wav").apply {
            // Minimal valid-ish payload (>44 bytes) for the client guard.
            writeBytes(ByteArray(128) { 1 })
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
        tmp.delete()
    }

    @Test
    fun recognizeParsesMockSuccess() {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"success","result":{"title":"Mock","artist":"Server"}}"""
            )
        )
        val client = AuddClient(
            apiToken = "test-token",
            endpoint = server.url("/").toString(),
        )
        val track = client.recognize(tmp)!!
        assertEquals("Mock", track.title)
        assertEquals("Server", track.artist)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        val body = recorded.body.readUtf8()
        assertTrueContains(body, "api_token")
        assertTrueContains(body, "test-token")
    }

    @Test
    fun recognizeHandlesNoMatch() {
        server.enqueue(MockResponse().setBody("""{"status":"success","result":null}"""))
        val client = AuddClient("tok", endpoint = server.url("/").toString())
        assertNull(client.recognize(tmp))
    }

    private fun assertTrueContains(hay: String, needle: String) {
        org.junit.Assert.assertTrue("Expected `$needle` in body", hay.contains(needle))
    }
}
