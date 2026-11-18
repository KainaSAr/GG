package com.musiclisten.app

/**
 * Musical lotto wins on a full horizontal, vertical, or diagonal line.
 */
object LineChecker {
    fun winningLines(
        size: Int,
        marked: BooleanArray,
        filled: BooleanArray = BooleanArray(marked.size) { true },
    ): List<List<Int>> {
        if (size < 2 || marked.size < size * size) return emptyList()
        fun complete(cells: List<Int>): Boolean =
            cells.all { marked[it] && filled.getOrElse(it) { false } }

        val lines = mutableListOf<List<Int>>()
        for (row in 0 until size) {
            val cells = (0 until size).map { row * size + it }
            if (complete(cells)) lines += cells
        }
        for (col in 0 until size) {
            val cells = (0 until size).map { it * size + col }
            if (complete(cells)) lines += cells
        }
        val diag = (0 until size).map { it * size + it }
        if (complete(diag)) lines += diag
        val anti = (0 until size).map { it * size + (size - 1 - it) }
        if (complete(anti)) lines += anti
        return lines
    }

    fun lineContaining(
        size: Int,
        marked: BooleanArray,
        filled: BooleanArray,
        index: Int,
    ): List<Int>? {
        val lines = winningLines(size, marked, filled)
        return lines.firstOrNull { index in it } ?: lines.firstOrNull()
    }

    fun label(cells: List<String>, line: List<Int>): String =
        line.joinToString(" · ") { index -> cells.getOrElse(index) { "" }.ifBlank { "?" } }

    fun goalReached(
        size: Int,
        marked: BooleanArray,
        target: WinTarget,
        filled: BooleanArray = BooleanArray(marked.size) { true },
    ): Boolean {
        if (size < 2 || marked.size < size * size) return false
        return when (target) {
            WinTarget.ONE_LINE -> winningLines(size, marked, filled).isNotEmpty()
            WinTarget.THREE_LINES -> winningLines(size, marked, filled).size >= 3
            WinTarget.FULL_CARD -> {
                val needed = (0 until size * size).filter { filled.getOrElse(it) { false } }
                needed.isNotEmpty() && needed.all { marked[it] }
            }
        }
    }
}

enum class WinTarget {
    ONE_LINE,
    THREE_LINES,
    FULL_CARD,
}
