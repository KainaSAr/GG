package com.musiclisten.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AcrResponseParserTest {
    @Test
    fun parsesFirstMusicHit() {
        val raw = """
            {"status":{"code":0,"msg":"Success"},"metadata":{"music":[
              {"title":"Лесник","artists":[{"name":"Король и Шут"}],"album":{"name":"Акустический"}}
            ]}}
        """.trimIndent()
        val track = AcrResponseParser.parse(raw)!!
        assertEquals("Лесник", track.title)
        assertEquals("Король и Шут", track.artist)
        assertEquals("Акустический", track.album)
    }

    @Test
    fun noResultIsNull() {
        assertNull(AcrResponseParser.parse("""{"status":{"code":1001,"msg":"No result"}}"""))
    }

    @Test
    fun quotaThrows() {
        assertThrows(ProviderQuotaException::class.java) {
            AcrResponseParser.parse("""{"status":{"code":3003,"msg":"limit"}}""")
        }
    }
}
