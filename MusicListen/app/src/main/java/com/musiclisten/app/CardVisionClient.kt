package com.musiclisten.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class CardVisionClient(
    private val apiKey: String,
    private val sessionId: String = UUID.randomUUID().toString(),
    private val endpoint: String = ENDPOINT,
    private val model: String = MODEL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    fun readCard(jpeg: ByteArray): CardDraft {
        require(apiKey.isNotBlank()) { "Нет ключа OpenCode Go" }
        require(jpeg.isNotEmpty()) { "Фото пустое" }

        val image = java.util.Base64.getEncoder().encodeToString(jpeg)
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", PROMPT))
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,$image"),
                    ),
            )
        val body = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", content),
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
                throw IllegalStateException(httpError(response.code, raw))
            }
            val root = JSONObject(raw)
            val text = root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (text.isBlank()) throw IllegalStateException("Пустой ответ модели")
            return CardDraftParser.parse(text)
        }
    }

    private fun httpError(code: Int, raw: String): String {
        val message = runCatching {
            JSONObject(raw).optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty()
        return if (message.isBlank()) "OpenCode Go HTTP $code" else "OpenCode Go: $message"
    }

    companion object {
        const val ENDPOINT = "https://opencode.ai/inference/go/openai/v1/chat/completions"
        const val MODEL = "qwen3.8-flash"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val PROMPT = """
            На фото бланк музыкального бинго. Сетка квадратная: 4×4, 5×5 или 6×6.
            Шапка B I N G O — не клетки, её не включай.
            В клетке может быть только число (39), или номер и кусок названия:
            «35.Ты так красива — Quest Pistols», «23.Лесник — Король и Шут».
            Верни только JSON:
            {"size":6,"cells":["35.Ты так красива — Quest Pistols","39"]}
            cells ровно size*size, слева направо, сверху вниз.
            Число без песни оставляй числом, не выдумывай название.
            Пустая клетка — "".
            Пиши так, как напечатано, не исправляй артистов.
        """.trimIndent()
    }
}
