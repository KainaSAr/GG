package com.musiclisten.app

import org.json.JSONObject

data class CardDraft(
    val size: Int,
    val cells: List<String>,
)

object CardDraftParser {
    fun parse(raw: String): CardDraft {
        val obj = JSONObject(extractJson(raw))
        val size = obj.optInt("size", 0)
        require(size in 4..6) { "Сетка должна быть 4×4, 5×5 или 6×6" }
        val arr = obj.optJSONArray("cells") ?: throw IllegalArgumentException("В ответе нет клеток")
        val expected = size * size
        require(arr.length() == expected) {
            "Ожидалось $expected клеток, пришло ${arr.length()}"
        }
        val cells = List(expected) { index -> arr.optString(index).trim() }
        return CardDraft(size, cells)
    }

    fun extractJson(raw: String): String {
        val fenced = Regex("```(?:json)?\\s*(\\{.*\\})\\s*```", RegexOption.DOT_MATCHES_ALL)
            .find(raw)
        val body = fenced?.groupValues?.get(1) ?: raw
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        require(start >= 0 && end > start) { "Модель не вернула бланк" }
        return body.substring(start, end + 1)
    }
}
