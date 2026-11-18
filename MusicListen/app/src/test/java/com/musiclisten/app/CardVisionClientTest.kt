package com.musiclisten.app

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardVisionClientTest {
    @Test
    fun sendsImageAndParsesCard() {
        val cells = JSONArray()
        repeat(16) { cells.put("t${it + 1}") }
        val card = JSONObject().put("size", 4).put("cells", cells)
        val body = JSONObject().put(
            "choices",
            JSONArray().put(JSONObject().put("message", JSONObject().put("content", card.toString()))),
        )
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(body.toString()))
        server.start()
        try {
            val client = CardVisionClient(
                apiKey = "secret-key",
                sessionId = "session-1",
                endpoint = server.url("/chat").toString(),
            )
            val draft = client.readCard(byteArrayOf(1, 2, 3, 4))
            assertEquals(4, draft.size)
            assertEquals("t1", draft.cells.first())
            assertEquals(16, draft.cells.size)

            val recorded = server.takeRequest()
            assertEquals("Bearer secret-key", recorded.getHeader("Authorization"))
            assertEquals("session-1", recorded.getHeader("x-opencode-session"))
            val sent = recorded.body.readUtf8()
            assertTrue(sent.contains("image_url"))
            assertTrue(sent.contains("data:image/jpeg;base64,"))
            assertTrue(!sent.contains("secret-key"))
        } finally {
            server.shutdown()
        }
    }
}
