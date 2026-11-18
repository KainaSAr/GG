package com.musiclisten.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AcrCloudClient(
    private val host: String,
    private val accessKey: String,
    private val accessSecret: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build(),
) {
    fun recognize(audioFile: File): TrackResult? {
        require(accessKey.isNotBlank() && accessSecret.isNotBlank()) { "ACRCloud keys are missing" }
        val timestamp = (System.currentTimeMillis() / 1000L).toString()
        val signature = sign(accessSecret, accessKey, timestamp)
        val endpoint = "https://${host.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')}/v1/identify"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("access_key", accessKey)
            .addFormDataPart("sample_bytes", audioFile.length().toString())
            .addFormDataPart("timestamp", timestamp)
            .addFormDataPart("signature", signature)
            .addFormDataPart("data_type", "audio")
            .addFormDataPart("signature_version", "1")
            .addFormDataPart(
                "sample",
                audioFile.name,
                audioFile.asRequestBody("audio/wav".toMediaType()),
            )
            .build()
        val request = Request.Builder().url(endpoint).post(body).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.code == 429) {
                throw ProviderQuotaException("ACRCloud", "ACRCloud HTTP 429")
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("ACRCloud HTTP ${response.code}")
            }
            return AcrResponseParser.parse(raw)
        }
    }

    companion object {
        fun sign(secret: String, accessKey: String, timestamp: String): String {
            val payload = "POST\n/v1/identify\n$accessKey\naudio\n1\n$timestamp"
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA1"))
            return java.util.Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))
        }
    }
}

object AcrResponseParser {
    fun parse(raw: String): TrackResult? {
        val root = JSONObject(raw)
        val status = root.optJSONObject("status")
        val code = status?.optInt("code", -1) ?: -1
        val message = status?.optString("msg").orEmpty()
        when (code) {
            0 -> Unit
            1001 -> return null
            3003 -> throw ProviderQuotaException("ACRCloud", message.ifBlank { "ACRCloud quota" })
            else -> throw IllegalStateException(message.ifBlank { "ACRCloud $code" })
        }
        val music = root.optJSONObject("metadata")?.optJSONArray("music") ?: return null
        val first = music.optJSONObject(0) ?: return null
        val title = first.optString("title").trim()
        if (title.isEmpty()) return null
        val artists = first.optJSONArray("artists")
        val artist = if (artists == null || artists.length() == 0) {
            "Unknown artist"
        } else {
            (0 until artists.length())
                .mapNotNull { artists.optJSONObject(it)?.optString("name")?.takeIf { name -> name.isNotBlank() } }
                .joinToString(", ")
                .ifBlank { "Unknown artist" }
        }
        val album = first.optJSONObject("album")?.optString("name")?.takeIf { it.isNotBlank() }
        return TrackResult(title, artist, album)
    }
}
