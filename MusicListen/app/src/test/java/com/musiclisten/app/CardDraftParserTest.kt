package com.musiclisten.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CardDraftParserTest {
    @Test
    fun parsesPlainJson() {
        val raw = """{"size":4,"cells":["А","Б","В","Г","Д","Е","Ж","З","И","К","Л","М","Н","О","П","Р"]}"""
        val draft = CardDraftParser.parse(raw)
        assertEquals(4, draft.size)
        assertEquals("А", draft.cells.first())
        assertEquals(16, draft.cells.size)
    }

    @Test
    fun stripsMarkdownFence() {
        val cells = (1..25).joinToString(",") { "\"песня $it\"" }
        val raw = "Вот бланк:\n```json\n{\"size\":5,\"cells\":[$cells]}\n```"
        val draft = CardDraftParser.parse(raw)
        assertEquals(5, draft.size)
        assertEquals("песня 1", draft.cells.first())
        assertEquals("песня 25", draft.cells.last())
    }

    @Test
    fun rejectsWrongCellCount() {
        try {
            CardDraftParser.parse("""{"size":4,"cells":["только одна"]}""")
            throw AssertionError("expected failure")
        } catch (e: IllegalArgumentException) {
            assertEquals(true, e.message?.contains("клеток"))
        }
    }
}
