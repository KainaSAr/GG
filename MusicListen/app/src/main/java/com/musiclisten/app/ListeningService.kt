package com.musiclisten.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
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
    private var wifiLock: WifiManager.WifiLock? = null
    private var screenRegistered = false
    private var restartScheduled = false
    private var keepAlive = true
    private var shownServiceText: String? = null
    private var lastStatus: String? = null
    private var lastAnswerLine: String? = null
    private var nextResultId = RESULT_ID_START

    private fun chain(): RecognitionChain = buildRecognitionChain(this)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                keepAlive = false
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
        acquireWifiLock()
        watchScreen()
        scope.launch(Dispatchers.IO) { RootKeepAlive.apply(packageName) }

        isRunning = true
        sendStatus(getString(R.string.status_watching))
        updateServiceText(getString(R.string.service_text_watching))

        loopJob = scope.launch {
            var musicStreak = 0
            var nextApiAllowedAt = 0L
            var lastNotifiedKey = ""
            var lastNotifiedAt = 0L
            var quietStreak = 0

            while (isActive) {
                try {
                    withContext(Dispatchers.IO) { monitor.ensureStarted() }
                    break
                } catch (t: Throwable) {
                    sendStatus(t.message ?: getString(R.string.status_error))
                    holdLocks()
                    delay(2_000L)
                }
            }

            while (isActive) {
                holdLocks()
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
                    var providerName = ""
                    val result = try {
                        withContext(Dispatchers.IO) {
                            monitor.captureWav(wav, ListenTiming.CAPTURE_MS)
                            val recognition = chain().recognize(wav)
                            providerName = recognition.provider
                            recognition.track
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
                                TicketEvent.NoCard, TicketEvent.Miss, TicketEvent.NeedKey ->
                                    publishAnswer(answer, result.title, vibrate = false)
                                TicketEvent.Already -> updateServiceText(answer)
                            }
                            sendTrack(result, event)
                            lastNotifiedKey = key
                            lastNotifiedAt = System.currentTimeMillis()
                        }

                        nextApiAllowedAt =
                            System.currentTimeMillis() + ListenTiming.COOLDOWN_AFTER_MATCH_MS
                        sendStatus(
                            getString(R.string.status_matched, result.title, result.artist) +
                                " · " + providerName
                        )
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

    private val screenEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            holdLocks()
            scope.launch(Dispatchers.IO) {
                runCatching { monitor.ensureStarted() }
            }
        }
    }

    private fun watchScreen() {
        if (screenRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenEvents, filter)
        screenRegistered = true
    }

    private fun unwatchScreen() {
        if (!screenRegistered) return
        runCatching { unregisterReceiver(screenEvents) }
        screenRegistered = false
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Listen::MicWatch").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        val wifi = applicationContext.getSystemService(WifiManager::class.java) ?: return
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wifi.createWifiLock(mode, "Listen::Net").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun holdLocks() {
        if (wakeLock?.isHeld != true) acquireWakeLock()
        if (wifiLock?.isHeld != true) acquireWifiLock()
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        wakeLock = null
        wifiLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        wifiLock = null
    }

    private fun applyToTicket(result: TrackResult): TicketEvent {
        val cells = TicketStore.cells(this)
        if (cells.none { it.isNotBlank() }) return TicketEvent.NoCard
        val goKey = TokenStore.goKey(this)
        if (goKey.isBlank()) return TicketEvent.NeedKey
        val hitIndex = NeuralCardMarker(goKey, TokenStore.goSession(this))
            .mark(cells, result.title, result.artist)
            ?: return TicketEvent.Miss
        if (hitIndex !in cells.indices || cells[hitIndex].isBlank()) return TicketEvent.Miss
        val hit = hitIndex
        val size = TicketStore.gridSize(this)
        val target = TicketStore.goal(this)
        val filled = BooleanArray(cells.size) { cells[it].isNotBlank() }
        val before = TicketStore.marks(this)
        val already = before.getOrElse(hit) { false }
        val metBefore = LineChecker.goalReached(size, before, target, filled)
        if (!already) TicketStore.setMarked(this, hit, true)
        val after = TicketStore.marks(this)
        if (!already && !metBefore && LineChecker.goalReached(size, after, target, filled)) {
            val line = LineChecker.lineContaining(size, after, filled, hit)
            val names = if (line != null) LineChecker.label(cells, line) else cells[hit]
            return TicketEvent.Line(hit, names)
        }
        if (!already) return TicketEvent.Cell(hit, cells[hit])
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
        val builder = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(getString(R.string.service_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.stop), stop)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (Prefs.wantRunning(this)) scheduleRestart()
        super.onTaskRemoved(rootIntent)
    }

    private fun stopSelfSafe() {
        loopJob?.cancel()
        loopJob = null
        monitor.release()
        unwatchScreen()
        releaseWakeLock()
        isRunning = false
        sendStatus(getString(R.string.status_stopped))
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        val restart = Prefs.wantRunning(this)
        loopJob?.cancel()
        monitor.release()
        unwatchScreen()
        releaseWakeLock()
        scope.cancel()
        isRunning = false
        if (restart && keepAlive) scheduleRestart()
        super.onDestroy()
    }

    private fun scheduleRestart() {
        if (restartScheduled) return
        restartScheduled = true
        val intent = Intent(this, ListeningService::class.java)
        val pending = PendingIntent.getService(
            this,
            7,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarm = getSystemService(AlarmManager::class.java)
        val whenMs = SystemClock.elapsedRealtime() + 1_500L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, whenMs, pending)
        } else {
            alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, whenMs, pending)
        }
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
            data object NeedKey : TicketEvent()
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
