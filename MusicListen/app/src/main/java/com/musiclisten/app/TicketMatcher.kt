package com.musiclisten.app

/**
 * Matches a recognized track to one cell on a musical-lotto card.
 * Cells may be a song title, "Artist — Title", or a short related phrase.
 * Short ambiguous tokens are ignored so a false mark does not complete a line.
 */
object TicketMatcher {
    const val MATCH_THRESHOLD = 0.72

    data class Hit(val index: Int, val score: Double)

    fun match(title: String, artist: String, cells: List<String>): Hit? {
        val titleN = normalize(title)
        val artistN = normalize(artist)
        val combo = listOf(artistN, titleN).filter { it.isNotEmpty() }.joinToString(" ")
        var best: Hit? = null
        cells.forEachIndexed { index, raw ->
            val cell = normalize(raw)
            if (!isSpecificEnough(cell)) return@forEachIndexed
            val score = score(cell, titleN, artistN, combo)
            if (score >= MATCH_THRESHOLD && (best == null || score > best!!.score)) {
                best = Hit(index, score)
            }
        }
        return best
    }

    fun normalize(raw: String): String {
        return raw.lowercase()
            .replace('ё', 'е')
            .replace(Regex("\\b(feat|ft|featuring)\\b\\.?"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isSpecificEnough(cell: String): Boolean {
        if (cell.length < 4) return false
        val tokens = cell.split(' ').filter { it.isNotBlank() }
        return tokens.any { it.length >= 4 }
    }

    internal fun score(cell: String, title: String, artist: String, combo: String): Double {
        if (cell.isEmpty() || title.isEmpty()) return 0.0
        if (cell == title || cell == combo || cell == "$title $artist".trim()) return 1.0

        val cellTokens = longTokens(cell)
        val titleTokens = longTokens(title)
        val trackTokens = longTokens("$title $artist")
        if (cellTokens.isEmpty() || titleTokens.isEmpty()) return 0.0

        // Every meaningful word on the card cell must belong to this track,
        // otherwise "Звезда" would mark "Звезда по имени Солнце".
        if (!cellTokens.all { it in trackTokens }) return 0.0
        if (titleTokens.all { it in cellTokens }) return 1.0

        val titleHits = cellTokens.count { it in titleTokens }
        if (titleHits == 0) return 0.0
        val coverage = titleHits.toDouble() / cellTokens.size
        return if (coverage >= 0.8) 0.86 else 0.0
    }

    private fun longTokens(text: String): Set<String> =
        text.split(' ').filter { it.length >= 4 }.toSet()
}
