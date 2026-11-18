package com.musiclisten.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val app = context.applicationContext
        val shouldStart = runCatching { Prefs.wantRunning(app) }.getOrDefault(false)
        if (shouldStart) {
            runCatching { ListeningService.start(app) }
        }
    }
}
