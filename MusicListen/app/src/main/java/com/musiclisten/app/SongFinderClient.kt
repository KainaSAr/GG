package com.musiclisten.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Free recognition with no account. Limited per IP, not by a monthly pack. */
class SongFinderClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(100, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val endpoint: String = ENDPOINT,
) {
    fun recognize(audioFile: File): TrackResult? {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("source", "cli")
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/wav".toMediaType()),
            )
            .build()
        val request = Request.Builder()
            .url(endpoint)
            .header("X-SongFinder-Client", "cli")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.code == 429) {
                throw IllegalStateException("Song Finder просит подождать несколько секунд")
            }
            if (!response.isSuccessful && raw.isBlank()) {
                throw IllegalStateException("Song Finder HTTP ${response.code}")
            }
            return SongFinderResponseParser.parse(raw)
        }
    }

    companion object {
        const val ENDPOINT = "https://songfinder.dev/api/music/recognize"
    }
}

object SongFinderResponseParser {
    fun parse(raw: String): TrackResult? {
        val root = JSONObject(raw)
        val code = root.optInt("code", 0)
        val message = root.optString("message")
        if (code != 0) {
            throw IllegalStateException(message.ifBlank { "Song Finder $code" })
        }
        val data = root.optJSONObject("data") ?: return null
        if (data.has("matched") && !data.optBoolean("matched")) return null
        val title = data.optString("title").trim()
        if (title.isEmpty()) return null
        val artist = data.optString("artist").trim().ifEmpty { "Unknown artist" }
        val album = data.optString("album").trim().takeIf { it.isNotEmpty() && it != "null" }
        return TrackResult(title, artist, album)
    }
}
