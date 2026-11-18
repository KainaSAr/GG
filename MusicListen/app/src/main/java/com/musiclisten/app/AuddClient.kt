package com.musiclisten.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class AuddClient(
    private val apiToken: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build(),
    private val endpoint: String = "https://api.audd.io/",
) {
    fun recognize(audioFile: File): TrackResult? {
        require(apiToken.isNotBlank()) { "AudD API token is missing" }
        require(audioFile.exists() && audioFile.length() > 44L) {
            "Audio file is missing or empty: ${audioFile.absolutePath}"
        }

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

        return execute(body)
    }

    fun recognizeUrl(audioUrl: String): TrackResult? {
        require(apiToken.isNotBlank()) { "AudD API token is missing" }
        require(audioUrl.isNotBlank()) { "Audio URL is blank" }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("api_token", apiToken)
            .addFormDataPart("return", "apple_music,spotify")
            .addFormDataPart("url", audioUrl)
            .build()

        return execute(body)
    }

    private fun execute(body: MultipartBody): TrackResult? {
        val request = Request.Builder()
            .url(endpoint)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("AudD HTTP ${response.code}: $raw")
            }
            return AuddResponseParser.parse(raw)
        }
    }
}
