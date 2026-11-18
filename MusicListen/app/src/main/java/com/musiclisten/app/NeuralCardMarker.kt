package com.musiclisten.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Picks the card cell for a recognized track. Cells on a real lotto sheet are
 * numbers or fragments like "23.Лесник — Король и Шут", so this is not a
 * string compare.
 */
class NeuralCardMarker(
    private val apiKey: String,
    private val sessionId: String,
    private val endpoint: String = CardVisionClient.ENDPOINT,
    private val model: String = CardVisionClient.MODEL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    fun mark(cells: List<String>, title: String, artist: String): Int? {
        require(apiKey.isNotBlank()) { "Нет ключа OpenCode Go" }
        val listing = cells.mapIndexed { index, cell ->
            "$index: ${cell.ifBlank { "(пусто)" }}"
        }.joinToString("\n")
        val prompt = """
            Бланк музыкального лото. В клетке бывает только число или строка
            вроде «23.Лесник — Король и Шут»: номер, кусок названия и артист.
            Клетки:
            $listing
            Сейчас играет: «$title» — $artist
            Верни только JSON {"index": N}.
            N — номер клетки с этой песней, или -1 если такой песни на бланке нет.
            Не отмечай клетку, где только число и нет названия.
            Другое написание и сокращение артиста — та же песня.
        """.trimIndent()
        val body = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", prompt),
                ),
            )
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", "Listen/1.0")
            .header("x-opencode-session", sessionId)
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("OpenCode Go HTTP ${response.code}")
            }
            val text = JSONObject(raw)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            return MarkResponseParser.parse(text)
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

object MarkResponseParser {
    fun parse(raw: String): Int? {
        val obj = JSONObject(CardDraftParser.extractJson(raw))
        if (!obj.has("index")) throw IllegalArgumentException("В ответе нет index")
        val index = obj.getInt("index")
        return index.takeIf { it >= 0 }
    }
}
