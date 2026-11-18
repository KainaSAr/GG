package com.musiclisten.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongFinderResponseParserTest {
    @Test
    fun parsesMatch() {
        val raw = """
            {"code":0,"data":{"matched":true,"title":"Happy Rock","artist":"Bensound","album":"EP"}}
        """.trimIndent()
        val track = SongFinderResponseParser.parse(raw)!!
        assertEquals("Happy Rock", track.title)
        assertEquals("Bensound", track.artist)
    }

    @Test
    fun unmatchedIsNull() {
        assertNull(
            SongFinderResponseParser.parse("""{"code":0,"data":{"matched":false}}"""),
        )
    }
}
