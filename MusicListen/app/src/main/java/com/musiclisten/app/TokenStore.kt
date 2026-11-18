package com.musiclisten.app

import android.content.Context

object TokenStore {
    private const val PREFS = "listen_prefs"
    private const val KEY = "audd_token"
    private const val GO_KEY = "opencode_go_key"
    private const val GO_SESSION = "opencode_go_session"
    private const val ACR_HOST = "acr_host"
    private const val ACR_KEY = "acr_key"
    private const val ACR_SECRET = "acr_secret"

    fun get(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "")
            .orEmpty()
            .trim()
        if (saved.isNotBlank()) return saved
        return BuildConfig.AUDD_API_TOKEN.trim()
    }

    fun set(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = token.trim()
        val previous = prefs.getString(KEY, "").orEmpty()
        prefs.edit().putString(KEY, next).apply()
        if (previous != next) ProviderState.clear(context, "AudD")
    }

    fun goKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(GO_KEY, "")
            .orEmpty()
            .trim()

    fun setGoKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(GO_KEY, key.trim())
            .apply()
    }

    fun acr(context: Context): AcrCredentials {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val host = prefs.getString(ACR_HOST, "").orEmpty().trim()
            .ifBlank { "identify-eu-west-1.acrcloud.com" }
        return AcrCredentials(
            host = host,
            accessKey = prefs.getString(ACR_KEY, "").orEmpty().trim(),
            accessSecret = prefs.getString(ACR_SECRET, "").orEmpty().trim(),
        )
    }

    fun setAcr(context: Context, host: String, accessKey: String, accessSecret: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val nextKey = accessKey.trim()
        val previous = prefs.getString(ACR_KEY, "").orEmpty()
        prefs.edit()
            .putString(ACR_HOST, host.trim())
            .putString(ACR_KEY, nextKey)
            .putString(ACR_SECRET, accessSecret.trim())
            .apply()
        if (previous != nextKey) ProviderState.clear(context, "ACRCloud")
    }

    fun goSession(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(GO_SESSION, "").orEmpty()
        if (existing.isNotBlank()) return existing
        val created = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(GO_SESSION, created).apply()
        return created
    }
}
