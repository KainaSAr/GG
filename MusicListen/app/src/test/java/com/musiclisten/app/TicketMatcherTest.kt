package com.musiclisten.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketMatcherTest {
    private val card = listOf(
        "Группа крови",
        "Yesterday",
        "Царица",
        "рок",
        "",
        "Кино — Звезда по имени Солнце",
        "Nothing else matters",
        "short",
    )

    @Test
    fun matchesTitleIgnoringCaseAndYo() {
        val hit = TicketMatcher.match("Группа крови", "Кино", card)
        assertEquals(0, hit!!.index)
        assertTrue(hit.score >= 0.9)
    }

    @Test
    fun matchesYoToE() {
        val hit = TicketMatcher.match("Елка", "Артист", listOf("Ёлка"))
        assertEquals(0, hit!!.index)
    }

    @Test
    fun matchesArtistTitleCell() {
        val hit = TicketMatcher.match("Звезда по имени Солнце", "Кино", card)
        assertEquals(5, hit!!.index)
    }

    @Test
    fun ignoresShortAmbiguousCell() {
        val hit = TicketMatcher.match("Рок-звезда", "Кто-то", card)
        assertNull(hit)
    }

    @Test
    fun doesNotMatchUnrelatedSong() {
        assertNull(TicketMatcher.match("Smells Like Teen Spirit", "Nirvana", card))
    }

    @Test
    fun normalizesPunctuation() {
        assertEquals("yesterday", TicketMatcher.normalize("Yesterday!"))
        assertEquals("царица", TicketMatcher.normalize("Царица —"))
    }
}
