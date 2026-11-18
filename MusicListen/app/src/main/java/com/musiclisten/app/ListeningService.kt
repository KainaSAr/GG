package com.musiclisten.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ListeningService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private val monitor = AudioMonitor()
    private val detector = MusicPresenceDetector()
    private var wakeLock: PowerManager.WakeLock? = null

    private fun audd(): AuddClient = AuddClient(TokenStore.get(this))

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Prefs.setWantRunning(this, false)
                stopSelfSafe()
                return START_NOT_STICKY
            }
            else -> {
                Prefs.setWantRunning(this, true)
                startListening()
            }
        }
        return START_STICKY
    }

    private fun startListening() {
        if (loopJob?.isActive == true) {
            promoteForeground()
            return
        }

        createChannels()
        promoteForeground()
        acquireWakeLock()

        isRunning = true
        sendStatus(getString(R.string.status_watching))
        updateServiceText(getString(R.string.service_text_watching))

        loopJob = scope.launch {
            var musicStreak = 0
            var nextApiAllowedAt = 0L
            var lastNotifiedKey = ""
            var lastNotifiedAt = 0L
            var quietStreak = 0

            try {
                withContext(Dispatchers.IO) { monitor.ensureStarted() }
            } catch (t: Throwable) {
                sendStatus(t.message ?: getString(R.string.status_error))
                stopSelfSafe()
                return@launch
            }

            while (isActive) {
                try {
                    // Local probe only — no network.
                    val probe = withContext(Dispatchers.IO) {
                        monitor.readPcm(PROBE_MS)
                    }
                    val verdict = detector.analyze(probe)

                    if (!verdict.isMusicLikely) {
                        musicStreak = 0
                        quietStreak++
                        // After music clearly stops, shorten remaining cooldown a bit
                        // so the next song can be identified without waiting the full window.
                        if (quietStreak == 4 && nextApiAllowedAt > 0L) {
                            val softened = System.currentTimeMillis() + 20_000L
                            if (softened < nextApiAllowedAt) nextApiAllowedAt = softened
                        }
                        sendStatus(getString(R.string.status_watching))
                        updateServiceText(getString(R.string.service_text_watching))
                        delay(PROBE_GAP_MS)
                        continue
                    }

                    quietStreak = 0
                    musicStreak++
                    sendStatus(
                        getString(R.string.status_music_nearby, "%.0f".format(verdict.score * 100))
                    )
                    updateServiceText(getString(R.string.service_text_music))

                    val now = System.currentTimeMillis()
                    if (musicStreak < REQUIRED_MUSIC_STREAK || now < nextApiAllowedAt) {
                        delay(PROBE_GAP_MS)
                        continue
                    }

                    // Confirmed sustained music → one AudD call.
                    musicStreak = 0
                    sendStatus(getString(R.string.status_identifying))
                    updateServiceText(getString(R.string.service_text_identifying))

                    val wav = File(cacheDir, "snippet_${System.currentTimeMillis()}.wav")
                    val result = try {
                        withContext(Dispatchers.IO) {
                            monitor.captureWav(wav, CAPTURE_MS)
                            audd().recognize(wav)
                        }
                    } finally {
                        wav.delete()
                    }

                    if (result != null) {
                        lastTitle = result.title
                        lastArtist = result.artist
                        lastAlbum = result.album.orEmpty()
                        Prefs.saveLastTrack(this@ListeningService, result.title, result.artist)

                        val key = "${result.title}|${result.artist}".lowercase()
                        val sameAsLast = key == lastNotifiedKey
                        val recentlySame =
                            sameAsLast && now - lastNotifiedAt < SAME_TRACK_SUPPRESS_MS
                        if (!recentlySame) {
                            showTrackMessage(result)
                            sendTrack(result)
                            lastNotifiedKey = key
                            lastNotifiedAt = now
                        }

                        nextApiAllowedAt = System.currentTimeMillis() + COOLDOWN_AFTER_MATCH_MS
                        sendStatus(getString(R.string.status_matched, result.title, result.artist))
                        updateServiceText(getString(R.string.service_text_watching))
                    } else {
                        nextApiAllowedAt = System.currentTimeMillis() + COOLDOWN_AFTER_MISS_MS
                        sendStatus(getString(R.string.status_error))
                        updateServiceText(getString(R.string.service_text_watching))
                    }
                } catch (t: Throwable) {
                    nextApiAllowedAt = System.currentTimeMillis() + COOLDOWN_AFTER_ERROR_MS
                    sendStatus(t.message ?: getString(R.string.status_error))
                    updateServiceText(getString(R.string.service_text_watching))
                    // Recreate mic session after errors.
                    runCatching {
                        withContext(Dispatchers.IO) {
                            monitor.release()
                            delay(400)
                            monitor.ensureStarted()
                        }
                    }
                }

                delay(PROBE_GAP_MS)
            }
        }
    }

    private fun promoteForeground() {
        val notification = buildQuietServiceNotification(getString(R.string.service_text_watching))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Listen::MicWatch",
        ).apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L) // refreshed by ongoing service use
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }

    private fun showTrackMessage(result: TrackResult) {
        val nm = getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(result.title)
            .setContentText(result.artist)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${result.title}\n${result.artist}")
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        nm.notify(RESULT_NOTIFICATION_ID, notification)
    }

    private fun buildQuietServiceNotification(text: String): Notification {
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(getString(R.string.service_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.stop), stop)
            .build()
    }

    private fun updateServiceText(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(SERVICE_NOTIFICATION_ID, buildQuietServiceNotification(text))
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL_ID,
                getString(R.string.service_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.service_channel_desc)
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                RESULT_CHANNEL_ID,
                getString(R.string.result_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.result_channel_desc)
            },
        )
    }

    private fun sendStatus(text: String) {
        sendBroadcast(
            Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_STATUS, text)
        )
    }

    private fun sendTrack(result: TrackResult) {
        sendBroadcast(
            Intent(ACTION_TRACK)
                .setPackage(packageName)
                .putExtra(EXTRA_TITLE, result.title)
                .putExtra(EXTRA_ARTIST, result.artist)
                .putExtra(EXTRA_ALBUM, result.album.orEmpty())
        )
    }

    private fun stopSelfSafe() {
        loopJob?.cancel()
        loopJob = null
        monitor.release()
        releaseWakeLock()
        isRunning = false
        sendStatus(getString(R.string.status_stopped))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        loopJob?.cancel()
        monitor.release()
        releaseWakeLock()
        scope.cancel()
        isRunning = false
        // If user still wants always-on, system may restart via START_STICKY / boot.
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.musiclisten.app.STOP"
        const val ACTION_STATUS = "com.musiclisten.app.STATUS"
        const val ACTION_TRACK = "com.musiclisten.app.TRACK"
        const val EXTRA_STATUS = "status"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ALBUM = "album"

        private const val SERVICE_CHANNEL_ID = "quiet_service"
        private const val RESULT_CHANNEL_ID = "track_message"
        private const val SERVICE_NOTIFICATION_ID = 41
        private const val RESULT_NOTIFICATION_ID = 42

        private const val PROBE_MS = 900L
        private const val PROBE_GAP_MS = 1_100L
        private const val CAPTURE_MS = 7_000L
        private const val REQUIRED_MUSIC_STREAK = 2
        private const val COOLDOWN_AFTER_MATCH_MS = 120_000L
        private const val COOLDOWN_AFTER_MISS_MS = 75_000L
        private const val COOLDOWN_AFTER_ERROR_MS = 90_000L
        private const val SAME_TRACK_SUPPRESS_MS = 180_000L

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var lastTitle: String = ""

        @Volatile
        var lastArtist: String = ""

        @Volatile
        var lastAlbum: String = ""

        fun start(context: Context) {
            val intent = Intent(context, ListeningService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            Prefs.setWantRunning(context, false)
            val intent = Intent(context, ListeningService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
