package com.musiclisten.app

import org.json.JSONObject

object AuddResponseParser {
    fun parse(raw: String): TrackResult? {
        val root = JSONObject(raw)
        if (root.optString("status") != "success") {
            val error = root.optJSONObject("error")
            val code = error?.optInt("error_code", -1) ?: -1
            val message = error?.optString("error_message")
                ?.takeIf { it.isNotBlank() }
                ?: root.toString()
            if (code == 901 || code == 902 || code == 903) {
                throw ProviderQuotaException("AudD", message)
            }
            throw IllegalStateException(message)
        }
        if (root.isNull("result")) return null
        val result = root.optJSONObject("result") ?: return null
        val title = result.optString("title").trim()
        if (title.isEmpty()) return null
        val artist = result.optString("artist").trim().ifEmpty { "Unknown artist" }
        val album = result.optString("album").trim().takeIf { it.isNotEmpty() }
        return TrackResult(title = title, artist = artist, album = album)
    }
}
