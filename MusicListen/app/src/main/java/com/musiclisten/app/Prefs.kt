package com.musiclisten.app

import android.content.Context

object Prefs {
    private const val NAME = "listen_prefs"
    private const val KEY_WANT_RUNNING = "want_running"
    private const val KEY_LAST_TITLE = "last_title"
    private const val KEY_LAST_ARTIST = "last_artist"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun setWantRunning(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_WANT_RUNNING, value).apply()
    }

    fun wantRunning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WANT_RUNNING, false)

    fun saveLastTrack(context: Context, title: String, artist: String) {
        prefs(context).edit()
            .putString(KEY_LAST_TITLE, title)
            .putString(KEY_LAST_ARTIST, artist)
            .apply()
    }

    fun lastTitle(context: Context): String =
        prefs(context).getString(KEY_LAST_TITLE, "").orEmpty()

    fun lastArtist(context: Context): String =
        prefs(context).getString(KEY_LAST_ARTIST, "").orEmpty()
}
