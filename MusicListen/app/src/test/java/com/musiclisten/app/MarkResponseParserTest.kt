package com.musiclisten.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkResponseParserTest {
    @Test
    fun parsesCellIndex() {
        assertEquals(23, MarkResponseParser.parse("""{"index": 23}"""))
    }

    @Test
    fun numberOnlyAbsenceIsNull() {
        assertNull(MarkResponseParser.parse("Песни нет.\n```json\n{\"index\": -1}\n```"))
    }

    @Test
    fun rejectsMissingIndex() {
        try {
            MarkResponseParser.parse("""{"cell":"Лесник"}""")
            throw AssertionError("expected failure")
        } catch (e: IllegalArgumentException) {
            assertEquals(true, e.message?.contains("index"))
        }
    }
}
