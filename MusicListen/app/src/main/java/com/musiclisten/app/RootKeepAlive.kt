package com.musiclisten.app

import java.util.concurrent.TimeUnit

/**
 * Ulefone Armor phones kill background work unless the app is whitelisted.
 * On a rooted Armor 30 this asks su to exempt Listen from Doze, standby and
 * background-network limits. Failures are ignored when root is not granted.
 */
object RootKeepAlive {
    fun apply(packageName: String): Boolean {
        if (!hasSu()) return false
        val commands = listOf(
            "dumpsys deviceidle whitelist +$packageName",
            "cmd deviceidle whitelist +$packageName",
            "cmd appops set $packageName RUN_IN_BACKGROUND allow",
            "cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow",
            "cmd appops set $packageName START_FOREGROUND allow",
            "cmd appops set $packageName WAKE_LOCK allow",
            "cmd appops set $packageName RECORD_AUDIO allow",
            "cmd netpolicy add restrict-background-whitelist $packageName",
        )
        var ok = false
        for (command in commands) {
            if (runSu(command)) ok = true
        }
        return ok
    }

    private fun hasSu(): Boolean {
        return runCatching {
            val which = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            val finished = which.waitFor(4, TimeUnit.SECONDS)
            val text = which.inputStream.bufferedReader().readText()
            if (!finished) which.destroy()
            finished && which.exitValue() == 0 && text.contains("uid=0")
        }.getOrDefault(false)
    }

    private fun runSu(command: String): Boolean {
        return runCatching {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(4, TimeUnit.SECONDS)
            process.inputStream.bufferedReader().readText()
            if (!finished) process.destroy()
            finished && process.exitValue() == 0
        }.getOrDefault(false)
    }
}
