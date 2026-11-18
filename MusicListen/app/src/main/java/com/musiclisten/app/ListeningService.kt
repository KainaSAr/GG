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
    private val recorder = AudioRecorder()
    private fun audd(): AuddClient = AuddClient(TokenStore.get(this))

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfSafe()
                return START_NOT_STICKY
            }
            else -> startListening()
        }
        return START_STICKY
    }

    private fun startListening() {
        if (loopJob?.isActive == true) return

        createChannels()
        val notification = buildQuietServiceNotification()
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

        isRunning = true
        sendStatus(getString(R.string.status_listening))

        loopJob = scope.launch {
            while (isActive) {
                try {
                    sendStatus(getString(R.string.status_identifying))
                    val wav = File(cacheDir, "snippet_${System.currentTimeMillis()}.wav")
                    withContext(Dispatchers.IO) {
                        recorder.recordToWav(wav, durationMs = 7_000L)
                    }
                    val result = withContext(Dispatchers.IO) {
                        audd().recognize(wav)
                    }
                    wav.delete()

                    if (result != null) {
                        lastTitle = result.title
                        lastArtist = result.artist
                        lastAlbum = result.album.orEmpty()
                        showTrackMessage(result)
                        sendTrack(result)
                    } else {
                        sendStatus(getString(R.string.status_error))
                    }
                } catch (t: Throwable) {
                    sendStatus(t.message ?: getString(R.string.status_error))
                }

                sendStatus(getString(R.string.status_listening))
                delay(4_000L)
            }
        }
    }

    private fun showTrackMessage(result: TrackResult) {
        val nm = getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Looks like a normal short message: title + artist only.
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        nm.notify(RESULT_NOTIFICATION_ID, notification)
    }

    private fun buildQuietServiceNotification(): Notification {
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(getString(R.string.service_title))
            .setContentText(getString(R.string.service_text))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.stop), stop)
            .build()
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
        isRunning = false
        sendStatus(getString(R.string.status_stopped))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        loopJob?.cancel()
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

        private const val SERVICE_CHANNEL_ID = "quiet_service"
        private const val RESULT_CHANNEL_ID = "track_message"
        private const val SERVICE_NOTIFICATION_ID = 41
        private const val RESULT_NOTIFICATION_ID = 42

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
            val intent = Intent(context, ListeningService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
