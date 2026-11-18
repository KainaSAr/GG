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
    private var wakeRefreshAt = 0L
    private var shownServiceText: String? = null
    private var lastStatus: String? = null
    private var lastAnswerLine: String? = null
    private var nextResultId = RESULT_ID_START

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
                refreshWakeLock()
                try {
                    val probe = withContext(Dispatchers.IO) {
                        monitor.readPcm(ListenTiming.PROBE_MS)
                    }
                    val verdict = detector.analyze(probe)

                    if (!verdict.isMusicLikely) {
                        musicStreak = 0
                        quietStreak++
                        // Host pauses between 15–30s clips. Drop the cooldown so the next song is eligible.
                        if (quietStreak >= ListenTiming.QUIET_RESET_STREAK) {
                            nextApiAllowedAt = 0L
                        }
                        sendStatus(getString(R.string.status_watching))
                        showIdleOrAnswer()
                        delay(ListenTiming.PROBE_GAP_MS)
                        continue
                    }

                    quietStreak = 0
                    // One loud window is not enough: a party produces single bursts
                    // that look tonal. Wait for a second confirming probe.
                    musicStreak += 1
                    sendStatus(
                        getString(R.string.status_music_nearby, "%.0f".format(verdict.score * 100))
                    )
                    showIdleOrAnswer()

                    val now = System.currentTimeMillis()
                    if (musicStreak < ListenTiming.REQUIRED_MUSIC_STREAK || now < nextApiAllowedAt) {
                        delay(ListenTiming.PROBE_GAP_MS)
                        continue
                    }

                    musicStreak = 0
                    sendStatus(getString(R.string.status_identifying))
                    updateServiceText(getString(R.string.service_text_identifying))

                    val wav = File(cacheDir, "snippet_${System.currentTimeMillis()}.wav")
                    val result = try {
                        withContext(Dispatchers.IO) {
                            monitor.captureWav(wav, ListenTiming.CAPTURE_MS)
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
                        val recentlySame =
                            key == lastNotifiedKey && now - lastNotifiedAt < ListenTiming.SAME_TRACK_SUPPRESS_MS
                        if (!recentlySame) {
                            val event = applyToTicket(result)
                            val answer = AnswerFormat.headline(result.title, result.artist)
                            lastAnswerLine = answer
                            when (event) {
                                is TicketEvent.Line -> publishAnswer(
                                    title = getString(R.string.line_notification_title),
                                    body = "$answer\n${event.label}",
                                    vibrate = true,
                                )
                                is TicketEvent.Cell -> publishAnswer(answer, event.label, vibrate = false)
                                TicketEvent.NoCard, TicketEvent.Miss -> publishAnswer(answer, result.title, vibrate = false)
                                TicketEvent.Already -> updateServiceText(answer)
                            }
                            sendTrack(result, event)
                            lastNotifiedKey = key
                            lastNotifiedAt = System.currentTimeMillis()
                        }

                        nextApiAllowedAt =
                            System.currentTimeMillis() + ListenTiming.COOLDOWN_AFTER_MATCH_MS
                        sendStatus(getString(R.string.status_matched, result.title, result.artist))
                        showIdleOrAnswer()
                    } else {
                        nextApiAllowedAt =
                            System.currentTimeMillis() + ListenTiming.COOLDOWN_AFTER_MISS_MS
                        sendStatus(getString(R.string.status_error))
                        showIdleOrAnswer()
                    }
                } catch (t: Throwable) {
                    nextApiAllowedAt =
                        System.currentTimeMillis() + ListenTiming.COOLDOWN_AFTER_ERROR_MS
                    sendStatus(t.message ?: getString(R.string.status_error))
                    showIdleOrAnswer()
                    runCatching {
                        withContext(Dispatchers.IO) {
                            monitor.release()
                            delay(400)
                            monitor.ensureStarted()
                        }
                    }
                }

                delay(ListenTiming.PROBE_GAP_MS)
            }
        }
    }

    private fun promoteForeground() {
        val text = lastAnswerLine ?: getString(R.string.service_text_watching)
        shownServiceText = text
        val notification = buildQuietServiceNotification(text)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        ServiceCompat.startForeground(this, SERVICE_NOTIFICATION_ID, notification, type)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Listen::MicWatch").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_CHUNK_MS)
        }
    }

    private fun refreshWakeLock() {
        val now = System.currentTimeMillis()
        if (wakeLock?.isHeld == true && now < wakeRefreshAt) return
        wakeRefreshAt = now + WAKE_REFRESH_MS
        val lock = wakeLock
        if (lock == null || !lock.isHeld) {
            acquireWakeLock()
            return
        }
        runCatching {
            lock.acquire(WAKE_LOCK_CHUNK_MS)
        }.onFailure {
            releaseWakeLock()
            acquireWakeLock()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        wakeLock = null
    }

    private fun applyToTicket(result: TrackResult): TicketEvent {
        val cells = TicketStore.cells(this)
        if (cells.none { it.isNotBlank() }) return TicketEvent.NoCard
        val hit = TicketMatcher.match(result.title, result.artist, cells) ?: return TicketEvent.Miss
        val size = TicketStore.gridSize(this)
        val target = TicketStore.goal(this)
        val filled = BooleanArray(cells.size) { cells[it].isNotBlank() }
        val before = TicketStore.marks(this)
        val already = before.getOrElse(hit.index) { false }
        val metBefore = LineChecker.goalReached(size, before, target, filled)
        if (!already) TicketStore.setMarked(this, hit.index, true)
        val after = TicketStore.marks(this)
        if (!already && !metBefore && LineChecker.goalReached(size, after, target, filled)) {
            val line = LineChecker.lineContaining(size, after, filled, hit.index)
            val names = if (line != null) LineChecker.label(cells, line) else cells[hit.index]
            return TicketEvent.Line(hit.index, names)
        }
        if (!already) return TicketEvent.Cell(hit.index, cells[hit.index].ifBlank { result.title })
        return TicketEvent.Already
    }

    private fun publishAnswer(title: String, body: String, vibrate: Boolean) {
        notifyResult(title, body, vibrate)
        updateServiceText(if (vibrate) "$title · $body" else title)
    }

    private fun showIdleOrAnswer() {
        updateServiceText(lastAnswerLine ?: getString(R.string.service_text_watching))
    }

    private fun notifyResult(title: String, body: String, vibrate: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$body"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVibrate(if (vibrate) longArrayOf(0, 140, 70, 140) else longArrayOf(0))
            .build()
        val id = nextResultId
        nextResultId = if (nextResultId >= RESULT_ID_END) RESULT_ID_START else nextResultId + 1
        nm.notify(id, notification)
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
        if (text == shownServiceText) return
        shownServiceText = text
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
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.result_channel_desc)
                enableVibration(true)
            },
        )
    }

    private fun sendStatus(text: String) {
        if (text == lastStatus) return
        lastStatus = text
        sendBroadcast(
            Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_STATUS, text)
        )
    }

    private fun sendTrack(result: TrackResult, event: TicketEvent) {
        val line = (event as? TicketEvent.Line)?.label.orEmpty()
        val cell = when (event) {
            is TicketEvent.Line -> event.index
            is TicketEvent.Cell -> event.index
            else -> -1
        }
        sendBroadcast(
            Intent(ACTION_TRACK)
                .setPackage(packageName)
                .putExtra(EXTRA_TITLE, result.title)
                .putExtra(EXTRA_ARTIST, result.artist)
                .putExtra(EXTRA_ALBUM, result.album.orEmpty())
                .putExtra(EXTRA_CELL, cell)
                .putExtra(EXTRA_LINE, line)
        )
    }

    private fun stopSelfSafe() {
        loopJob?.cancel()
        loopJob = null
        monitor.release()
        releaseWakeLock()
        isRunning = false
        sendStatus(getString(R.string.status_stopped))
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        loopJob?.cancel()
        monitor.release()
        releaseWakeLock()
        scope.cancel()
        isRunning = false
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
        const val EXTRA_CELL = "cell"
        const val EXTRA_LINE = "line"

        private sealed class TicketEvent {
            data object NoCard : TicketEvent()
            data object Miss : TicketEvent()
            data object Already : TicketEvent()
            data class Cell(val index: Int, val label: String) : TicketEvent()
            data class Line(val index: Int, val label: String) : TicketEvent()
        }

        private const val SERVICE_CHANNEL_ID = "quiet_service"
        private const val RESULT_CHANNEL_ID = "track_message_v2"
        private const val SERVICE_NOTIFICATION_ID = 41
        private const val RESULT_ID_START = 100
        private const val RESULT_ID_END = 104
        private const val WAKE_LOCK_CHUNK_MS = 30 * 60 * 1000L
        private const val WAKE_REFRESH_MS = 5 * 60 * 1000L

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
