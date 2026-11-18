package com.musiclisten.app

import android.content.Context
import org.json.JSONArray

object TicketStore {
    private const val PREFS = "listen_prefs"
    private const val KEY_SIZE = "ticket_size"
    private const val KEY_CELLS = "ticket_cells"
    private const val KEY_MARKS = "ticket_marks"
    private const val KEY_GOAL = "ticket_goal"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun gridSize(context: Context): Int =
        prefs(context).getInt(KEY_SIZE, 4).coerceIn(4, 6)

    fun setGridSize(context: Context, size: Int) {
        val next = size.coerceIn(4, 6)
        val kept = cells(context)
        val count = next * next
        val fresh = List(count) { kept.getOrElse(it) { "" } }
        prefs(context).edit()
            .putInt(KEY_SIZE, next)
            .putString(KEY_CELLS, JSONArray(fresh).toString())
            .putString(KEY_MARKS, emptyMarks(count))
            .apply()
    }

    fun cells(context: Context): List<String> {
        val count = gridSize(context) * gridSize(context)
        val arr = readArray(prefs(context).getString(KEY_CELLS, "[]"))
        return List(count) { index -> arr.optString(index, "") }
    }

    fun setCell(context: Context, index: Int, value: String) {
        val list = cells(context).toMutableList()
        if (index !in list.indices) return
        list[index] = value
        prefs(context).edit().putString(KEY_CELLS, JSONArray(list).toString()).apply()
    }

    fun marks(context: Context): BooleanArray {
        val count = gridSize(context) * gridSize(context)
        val arr = readArray(prefs(context).getString(KEY_MARKS, "[]"))
        return BooleanArray(count) { index -> arr.optBoolean(index, false) }
    }

    fun isMarked(context: Context, index: Int): Boolean =
        marks(context).getOrElse(index) { false }

    fun setMarked(context: Context, index: Int, marked: Boolean) {
        val current = marks(context)
        if (index !in current.indices) return
        current[index] = marked
        val json = JSONArray()
        current.forEach { json.put(it) }
        prefs(context).edit().putString(KEY_MARKS, json.toString()).apply()
    }

    fun goal(context: Context): WinTarget {
        val index = prefs(context).getInt(KEY_GOAL, 0)
        return WinTarget.entries.getOrElse(index) { WinTarget.ONE_LINE }
    }

    fun setGoal(context: Context, target: WinTarget) {
        prefs(context).edit().putInt(KEY_GOAL, target.ordinal).apply()
    }

    fun replaceCard(context: Context, size: Int, cells: List<String>) {
        val next = size.coerceIn(4, 6)
        val count = next * next
        val fresh = List(count) { cells.getOrElse(it) { "" }.trim() }
        prefs(context).edit()
            .putInt(KEY_SIZE, next)
            .putString(KEY_CELLS, JSONArray(fresh).toString())
            .putString(KEY_MARKS, emptyMarks(count))
            .apply()
    }

    fun clearMarks(context: Context) {
        val count = gridSize(context) * gridSize(context)
        prefs(context).edit().putString(KEY_MARKS, emptyMarks(count)).apply()
    }

    private fun emptyMarks(count: Int): String {
        val json = JSONArray()
        repeat(count) { json.put(false) }
        return json.toString()
    }

    private fun readArray(raw: String?): JSONArray =
        runCatching { JSONArray(raw ?: "[]") }.getOrElse { JSONArray() }
}
