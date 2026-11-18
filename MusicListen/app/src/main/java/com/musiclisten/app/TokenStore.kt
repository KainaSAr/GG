package com.musiclisten.app

import android.content.Context

object TokenStore {
    private const val PREFS = "listen_prefs"
    private const val KEY = "audd_token"

    fun get(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "")
            .orEmpty()
            .trim()
        if (saved.isNotBlank()) return saved
        return BuildConfig.AUDD_API_TOKEN.trim()
    }

    fun set(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, token.trim())
            .apply()
    }
}
