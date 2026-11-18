package com.musiclisten.app

import android.content.Context

object ProviderState {
    private const val PREFS = "listen_prefs"
    private const val KEY = "exhausted_providers"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isExhausted(context: Context, provider: String): Boolean =
        prefs(context).getStringSet(KEY, emptySet()).orEmpty().contains(provider)

    fun markExhausted(context: Context, provider: String) {
        val next = prefs(context).getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        next += provider
        prefs(context).edit().putStringSet(KEY, next).apply()
    }

    fun clear(context: Context, provider: String) {
        val next = prefs(context).getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        if (next.remove(provider)) {
            prefs(context).edit().putStringSet(KEY, next).apply()
        }
    }
}
