package com.musiclisten.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RockEraTest {
    @Test
    fun classicHeadliners() {
        assertEquals(RockEra.CLASSIC, RockEra.label("Кино"))
        assertEquals(RockEra.CLASSIC, RockEra.label("ДДТ"))
        assertEquals(RockEra.CLASSIC, RockEra.label("Король и Шут"))
        assertEquals(RockEra.CLASSIC, RockEra.label("Ария"))
        assertEquals(RockEra.CLASSIC, RockEra.label("Наутилус Помпилиус"))
    }

    @Test
    fun newScene() {
        assertEquals(RockEra.NEW, RockEra.label("Земфира"))
        assertEquals(RockEra.NEW, RockEra.label("Би-2"))
        assertEquals(RockEra.NEW, RockEra.label("Сплин"))
        assertEquals(RockEra.NEW, RockEra.label("Мумий Тролль"))
    }

    @Test
    fun unknownStaysUntagged() {
        assertNull(RockEra.label("Queen"))
        assertNull(RockEra.label(""))
    }

    @Test
    fun headlineIncludesEra() {
        assertEquals(
            "Кино — Группа крови · Классика",
            AnswerFormat.headline("Группа крови", "Кино"),
        )
    }
}
