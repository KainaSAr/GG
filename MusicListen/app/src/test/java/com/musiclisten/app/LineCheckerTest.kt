package com.musiclisten.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineCheckerTest {
    @Test
    fun rowWinsOn4x4() {
        val marked = BooleanArray(16)
        for (i in 0 until 4) marked[i] = true
        val lines = LineChecker.winningLines(4, marked)
        assertEquals(listOf(listOf(0, 1, 2, 3)), lines)
    }

    @Test
    fun columnAndDiagonal() {
        val marked = BooleanArray(16) { true }
        val lines = LineChecker.winningLines(4, marked)
        assertEquals(10, lines.size) // 4 rows + 4 cols + 2 diags
        assertTrue(lines.any { it == listOf(0, 5, 10, 15) })
        assertTrue(lines.any { it == listOf(3, 6, 9, 12) })
    }

    @Test
    fun incompleteIsNotAWin() {
        val marked = BooleanArray(16)
        marked[0] = true
        marked[1] = true
        marked[2] = true
        assertTrue(LineChecker.winningLines(4, marked).isEmpty())
    }

    @Test
    fun fiveByFiveNeedsFive() {
        val marked = BooleanArray(25)
        for (i in 0 until 4) marked[i] = true
        assertTrue(LineChecker.winningLines(5, marked).isEmpty())
        marked[4] = true
        assertEquals(listOf(0, 1, 2, 3, 4), LineChecker.winningLines(5, marked).single())
    }

    @Test
    fun threeLinesAndFullCard() {
        val marked = BooleanArray(16)
        for (row in 0 until 3) {
            for (col in 0 until 4) marked[row * 4 + col] = true
        }
        assertTrue(LineChecker.goalReached(4, marked, WinTarget.THREE_LINES))
        assertTrue(!LineChecker.goalReached(4, marked, WinTarget.FULL_CARD))
        for (i in marked.indices) marked[i] = true
        assertTrue(LineChecker.goalReached(4, marked, WinTarget.FULL_CARD))
    }

    @Test
    fun blankCellDoesNotCompleteALineOrFullCard() {
        val marked = BooleanArray(16) { it < 4 }
        val filled = BooleanArray(16) { it != 3 }
        assertTrue(LineChecker.winningLines(4, marked, filled).isEmpty())
        assertTrue(!LineChecker.goalReached(4, marked, WinTarget.FULL_CARD, filled))
        for (i in 0 until 3) marked[i] = true
        val onlyThree = BooleanArray(16) { it < 3 }
        assertTrue(LineChecker.goalReached(4, marked, WinTarget.FULL_CARD, onlyThree))
    }

    @Test
    fun prefersTheLineThatContainsTheNewCell() {
        val marked = BooleanArray(16)
        for (i in 0 until 4) marked[i] = true
        marked[4] = true
        marked[8] = true
        marked[12] = true
        val filled = BooleanArray(16) { true }
        assertEquals(listOf(0, 4, 8, 12), LineChecker.lineContaining(4, marked, filled, 12))
    }

    @Test
    fun labelUsesCellText() {
        val cells = listOf("A", "B", "C", "D")
        assertEquals("A · B", LineChecker.label(cells, listOf(0, 1)))
    }
}
