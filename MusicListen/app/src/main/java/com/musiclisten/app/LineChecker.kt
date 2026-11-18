package com.musiclisten.app

/**
 * Musical lotto wins on a full horizontal, vertical, or diagonal line.
 */
object LineChecker {
    fun winningLines(size: Int, marked: BooleanArray): List<List<Int>> {
        if (size < 2 || marked.size < size * size) return emptyList()
        val lines = mutableListOf<List<Int>>()
        for (row in 0 until size) {
            val cells = (0 until size).map { row * size + it }
            if (cells.all { marked[it] }) lines += cells
        }
        for (col in 0 until size) {
            val cells = (0 until size).map { it * size + col }
            if (cells.all { marked[it] }) lines += cells
        }
        val diag = (0 until size).map { it * size + it }
        if (diag.all { marked[it] }) lines += diag
        val anti = (0 until size).map { it * size + (size - 1 - it) }
        if (anti.all { marked[it] }) lines += anti
        return lines
    }

    fun label(cells: List<String>, line: List<Int>): String =
        line.joinToString(" · ") { index -> cells.getOrElse(index) { "" }.ifBlank { "?" } }

    fun goalReached(size: Int, marked: BooleanArray, target: WinTarget): Boolean {
        if (size < 2 || marked.size < size * size) return false
        return when (target) {
            WinTarget.ONE_LINE -> winningLines(size, marked).isNotEmpty()
            WinTarget.THREE_LINES -> winningLines(size, marked).size >= 3
            WinTarget.FULL_CARD -> marked.copyOf(size * size).all { it }
        }
    }
}

enum class WinTarget {
    ONE_LINE,
    THREE_LINES,
    FULL_CARD,
}
