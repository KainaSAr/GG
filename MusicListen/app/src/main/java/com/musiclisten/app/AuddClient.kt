package com.musiclisten.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class AuddClient(
    private val apiToken: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build(),
) {
    fun recognize(audioFile: File): TrackResult? {
        require(apiToken.isNotBlank()) { "AudD API token is missing" }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("api_token", apiToken)
            .addFormDataPart("return", "apple_music,spotify")
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/wav".toMediaType()),
            )
            .build()

        val request = Request.Builder()
            .url("https://api.audd.io/")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("AudD HTTP ${response.code}: $raw")
            }
            return parse(raw)
        }
    }

    private fun parse(raw: String): TrackResult? {
        val root = JSONObject(raw)
        if (root.optString("status") != "success") {
            val error = root.optJSONObject("error")
            val message = error?.optString("error_message") ?: root.toString()
            throw IllegalStateException(message)
        }
        val result = root.optJSONObject("result") ?: return null
        val title = result.optString("title").ifBlank { return null }
        val artist = result.optString("artist").ifBlank { "Unknown artist" }
        val album = result.optString("album").takeIf { it.isNotBlank() }
        return TrackResult(title = title, artist = artist, album = album)
    }
}
