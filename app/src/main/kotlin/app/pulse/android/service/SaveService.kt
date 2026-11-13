package app.pulse.android.service

import android.app.Service
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import app.pulse.android.Database
import app.pulse.android.R
import app.pulse.android.utils.LocalDownloads
import app.pulse.android.utils.SaveProgressListener
import app.pulse.android.utils.SaveProgressThrottle
import app.pulse.android.utils.SaveQueue
import app.pulse.android.utils.SaveRequest
import app.pulse.android.utils.SaveResult
import app.pulse.android.utils.savePercent
import app.pulse.android.utils.toast
import java.util.ArrayDeque

class SaveService : Service() {
    private data class Queued(val request: SaveRequest, val startId: Int)

    private val lock = Any()
    private val queue = ArrayDeque<Queued>()
    private val keys = SaveQueue()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var currentRequest: SaveRequest? = null
    @Volatile private var cancelled = false
    @Volatile private var foregroundStarted = false
    @Volatile private var lastPercent = -1
    private var lastStartId = 0

    override fun onCreate() {
        super.onCreate()
        scope.launch { process() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        synchronized(lock) { lastStartId = startId }
        if (intent?.action == ACTION_CANCEL) {
            cancelAll()
            return START_NOT_STICKY
        }
        val request = intent?.toSaveRequest()
        if (request == null) {
            maybeStop()
            return START_NOT_STICKY
        }

        val accepted = keys.offer(request.baseName)
        if (accepted) synchronized(lock) { queue.addLast(Queued(request, startId)) }

        refreshNotification()

        if (!accepted) {
            toast(R.string.already_downloading)
            maybeStop()
            return START_NOT_STICKY
        }

        wake.trySend(Unit)
        return START_NOT_STICKY
    }

    private fun cancelAll() {
        cancelled = true
        synchronized(lock) {
            queue.clear()
            keys.clear()
        }
        if (currentRequest == null && waitingCount() == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(lastStartId)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun process() {
        for (signal in wake) {
            while (true) {
                val item = synchronized(lock) {
                    val first = queue.peekFirst()
                    if (first != null) queue.removeFirst()
                    first
                } ?: break
                currentRequest = item.request
                cancelled = false
                lastPercent = -1
                showProgress(item.request, percent = null, waiting = waitingCount())

                val result = try {
                    LocalDownloads.saveNow(this, item.request, listener(item.request))
                } catch (t: Throwable) {
                    SaveResult.Failed(t)
                } finally {
                    keys.remove(item.request.baseName)
                }

                currentRequest = null
                if (waitingCount() == 0) settle(result, item.request) else toast(messageFor(result))
            }
        }
    }

    private fun listener(request: SaveRequest): SaveProgressListener {
        val throttle = SaveProgressThrottle()
        return object : SaveProgressListener {
            override fun onProgress(downloaded: Long, total: Long) {
                val percent = savePercent(downloaded, total)
                if (percent != null && throttle.shouldEmit(percent, SystemClock.elapsedRealtime())) {
                    lastPercent = percent
                    showProgress(request, percent, waitingCount())
                }
            }

            override fun isCancelled(): Boolean = cancelled
        }
    }

    private fun showProgress(request: SaveRequest, percent: Int?, waiting: Int) {
        val builder = NotificationCompat.Builder(this, ServiceNotifications.save.id)
            .setSmallIcon(R.drawable.download)
            .setContentTitle(getString(R.string.saving_song, request.title.ifBlank { request.baseName }))
            .setContentText(
                if (percent != null) getString(R.string.saving_percent, percent)
                else ""
            )
            .setSubText(if (waiting > 0) getString(R.string.save_waiting, waiting) else "")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, percent == null)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.close,
                    getString(R.string.cancel),
                    PendingIntent.getService(
                        this@SaveService,
                        0,
                        Intent(this@SaveService, SaveService::class.java).setAction(ACTION_CANCEL),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                ).build()
            )

        val songName = request.title.ifBlank { request.baseName }
        val content = RemoteViews(packageName, R.layout.notification_save).apply {
            setTextViewText(R.id.save_notification_title, getString(R.string.saving_song, songName))
            setTextViewText(
                R.id.save_notification_percent,
                if (percent != null) getString(R.string.saving_percent, percent) else ""
            )
            setViewVisibility(
                R.id.save_notification_percent,
                if (percent != null) View.VISIBLE else View.GONE
            )
            setProgressBar(R.id.save_notification_progress, 100, percent ?: 0, percent == null)
        }
        builder
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(content)

        if (!foregroundStarted) {
            ServiceNotifications.save.startForeground(this, { builder })
            foregroundStarted = true
        } else {
            ServiceNotifications.save.sendNotification(this, { builder })
        }
    }

    private fun refreshNotification() {
        currentRequest?.let { req ->
            showProgress(req, lastPercent.takeIf { it >= 0 }, waitingCount())
            return
        }
        synchronized(lock) {
            queue.peekFirst()?.let { req ->
                showProgress(req.request, null, waitingCount())
            }
        }
    }

    private fun messageFor(result: SaveResult): Int = when (result) {
        is SaveResult.Saved -> R.string.saved_to_local
        is SaveResult.AlreadySaved -> R.string.already_saved_to_local
        is SaveResult.Cancelled -> R.string.save_cancelled
        is SaveResult.MissingPermission -> R.string.grant_file_access_to_save
        is SaveResult.Failed -> R.string.save_to_local_failed
    }

    private fun settle(result: SaveResult, request: SaveRequest) {
        val title = getString(messageFor(result))
        val text = request.title.ifBlank { request.baseName }

        toast(messageFor(result))

        val builder = NotificationCompat.Builder(this, ServiceNotifications.save.id)
            .setSmallIcon(R.drawable.download)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)

        ServiceNotifications.save.sendNotification(this, { builder })

        scope.launch(Dispatchers.Main) {
            delay(1500)
            if (waitingCount() > 0) return@launch
            ServiceNotifications.save.cancel(this@SaveService)
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
            maybeStop()
        }
    }

    private fun maybeStop() {
        synchronized(lock) {
            if (currentRequest == null && queue.isEmpty()) {
                stopSelf(lastStartId)
            }
        }
    }

    private fun waitingCount(): Int = synchronized(lock) { queue.size }

    private fun toast(@androidx.annotation.StringRes message: Int) {
        mainHandler.post { toast(message) }
    }

    companion object {
        private const val EXTRA_VIDEO_ID = "video_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTIST = "artist"
        private const val EXTRA_DURATION_TEXT = "duration_text"
        private const val EXTRA_LIKED_AT = "liked_at"

        private const val ACTION_CANCEL = "app.pulse.android.action.CANCEL_SAVE"

        fun enqueue(context: Context, mediaItem: androidx.media3.common.MediaItem, likedAt: Long?) {
            val request = SaveRequest.from(mediaItem, likedAt)
            val intent = Intent(context, SaveService::class.java)
                .putExtra(EXTRA_VIDEO_ID, request.videoId)
                .putExtra(EXTRA_TITLE, request.title)
                .putExtra(EXTRA_ARTIST, request.artist)
                .putExtra(EXTRA_DURATION_TEXT, request.durationText)
                .putExtra(EXTRA_LIKED_AT, request.likedAt ?: -1L)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, SaveService::class.java).setAction(ACTION_CANCEL)
            )
        }

        private fun Intent.toSaveRequest(): SaveRequest? {
            val videoId = getStringExtra(EXTRA_VIDEO_ID) ?: return null
            val title = getStringExtra(EXTRA_TITLE) ?: ""
            val artist = getStringExtra(EXTRA_ARTIST)
            val durationText = getStringExtra(EXTRA_DURATION_TEXT) ?: ""
            val likedAt = getLongExtra(EXTRA_LIKED_AT, -1L).takeIf { it >= 0 }
            return SaveRequest(videoId, title, artist, durationText, likedAt)
        }
    }
}