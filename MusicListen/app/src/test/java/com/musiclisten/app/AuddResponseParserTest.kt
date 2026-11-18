package com.musiclisten.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AuddResponseParserTest {
    @Test
    fun parsesSuccess() {
        val raw = """
            {"status":"success","result":{"title":"Song","artist":"Band","album":"LP"}}
        """.trimIndent()
        val track = AuddResponseParser.parse(raw)!!
        assertEquals("Song", track.title)
        assertEquals("Band", track.artist)
        assertEquals("LP", track.album)
    }

    @Test
    fun nullResultMeansNoMatch() {
        val raw = """{"status":"success","result":null}"""
        assertNull(AuddResponseParser.parse(raw))
    }

    @Test
    fun blankTitleMeansNoMatch() {
        val raw = """{"status":"success","result":{"title":"  ","artist":"X"}}"""
        assertNull(AuddResponseParser.parse(raw))
    }

    @Test
    fun quotaSwitchesProvider() {
        val raw = """
            {"status":"error","error":{"error_code":902,"error_message":"quota"}}
        """.trimIndent()
        val ex = assertThrows(ProviderQuotaException::class.java) {
            AuddResponseParser.parse(raw)
        }
        assertEquals("AudD", ex.provider)
    }

    @Test
    fun errorStatusThrows() {
        val raw = """
            {"status":"error","error":{"error_code":900,"error_message":"bad token"}}
        """.trimIndent()
        val ex = assertThrows(IllegalStateException::class.java) {
            AuddResponseParser.parse(raw)
        }
        assertEquals("bad token", ex.message)
    }

    @Test
    fun missingArtistFallsBack() {
        val raw = """{"status":"success","result":{"title":"Solo"}}"""
        val track = AuddResponseParser.parse(raw)!!
        assertEquals("Unknown artist", track.artist)
    }
}
