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
        if (cell.isEmpty()) return 0.0
        if (cell == title || cell == artist || cell == combo) return 1.0
        if (cell.length >= 4 && (combo.contains(cell) || (title.isNotEmpty() && title.contains(cell)))) {
            return 0.95
        }
        if (title.length >= 4 && cell.contains(title)) return 0.92

        val cellTokens = tokens(cell)
        val trackTokens = tokens("$title $artist")
        if (cellTokens.isEmpty() || trackTokens.isEmpty()) return ratio(cell, title)

        val shared = cellTokens.intersect(trackTokens)
        if (shared.isEmpty() || shared.none { it.length >= 4 }) return ratio(cell, title) * 0.5
        val coverage = shared.size.toDouble() / cellTokens.size
        val jaccard = shared.size.toDouble() / cellTokens.union(trackTokens).size
        return maxOf(coverage * 0.82 + jaccard * 0.18, ratio(cell, title))
    }

    private fun tokens(text: String): Set<String> =
        text.split(' ').filter { it.length >= 3 }.toSet()

    private fun ratio(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val dist = levenshtein(a, b)
        return 1.0 - dist.toDouble() / maxOf(a.length, b.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in a.indices) {
            cur[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                cur[j + 1] = minOf(cur[j] + 1, prev[j + 1] + 1, prev[j] + cost)
            }
            for (j in prev.indices) prev[j] = cur[j]
        }
        return prev[b.length]
    }
}
