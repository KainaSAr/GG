package com.musiclisten.app

/**
 * Era tag for the "Классика vs Новый" Russian-rock night.
 * Unknown artists stay untagged so a guess is not pushed to the wrong side.
 */
object RockEra {
    const val CLASSIC = "Классика"
    const val NEW = "Новый"

    private val classic = listOf(
        "кино",
        "цой",
        "ддт",
        "шевчук",
        "ария",
        "король и шут",
        "киш",
        "наутилус",
        "аквариум",
        "гребенщиков",
        "алиса",
        "кинчев",
        "чайф",
        "агата кристи",
        "машина времени",
        "браво",
        "крематорий",
        "пикник",
        "сектор газа",
        "воскресение",
        "зоопарк",
    )

    private val newer = listOf(
        "сплин",
        "би-2",
        "би 2",
        "би2",
        "земфира",
        "мумий тролль",
        "мумий тролл",
        "смысловые галлюцинации",
        "нервы",
        "порнофильмы",
        "animal джаз",
        "animal jazz",
        "звери",
        "мозги",
    )

    fun label(artist: String): String? {
        val name = TicketMatcher.normalize(artist)
        if (name.isBlank()) return null
        if (newer.any { hits(name, TicketMatcher.normalize(it)) }) return NEW
        if (classic.any { hits(name, TicketMatcher.normalize(it)) }) return CLASSIC
        return null
    }

    private fun hits(artist: String, alias: String): Boolean {
        if (artist == alias) return true
        if (alias.length >= 6 && artist.contains(alias)) return true
        return artist.startsWith("$alias ") || artist.endsWith(" $alias") || artist.contains(" $alias ")
    }
}

object AnswerFormat {
    fun headline(title: String, artist: String): String {
        val who = artist.ifBlank { "—" }
        val era = RockEra.label(artist)
        val base = "$who — $title"
        return if (era == null) base else "$base · $era"
    }
}
