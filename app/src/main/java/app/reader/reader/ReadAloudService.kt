package app.reader.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.PowerManager
import app.reader.R
import app.reader.ReaderActivity

/**
 * Keeps read-aloud going with the screen off: a foreground media service with a media
 * session, so play/pause/skip work from the lock screen, the notification and headphones.
 * The speaking itself stays in [ReadAloud]; this service only keeps the process awake and
 * relays the controls.
 */
class ReadAloudService : Service() {
    interface Controls {
        fun playPause()
        fun next()
        fun previous()
        fun stop()
    }

    private var session: MediaSession? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Read aloud", NotificationManager.IMPORTANCE_LOW))
        session = MediaSession(this, "ReadAloud").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { controls?.playPause() }
                override fun onPause() { controls?.playPause() }
                override fun onSkipToNext() { controls?.next() }
                override fun onSkipToPrevious() { controls?.previous() }
                override fun onStop() { controls?.stop() }
            })
            isActive = true
        }
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "reader:read-aloud")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> controls?.playPause()
            ACTION_NEXT -> controls?.next()
            ACTION_PREVIOUS -> controls?.previous()
            ACTION_STOP -> { controls?.stop(); return START_NOT_STICKY }
        }
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: current.title
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: current.label
        val playing = intent?.getBooleanExtra(EXTRA_PLAYING, current.playing) ?: current.playing
        current = Info(title, label, playing)
        show(current)
        return START_NOT_STICKY
    }

    private fun show(i: Info) {
        val s = session ?: return
        s.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, i.label.ifEmpty { i.title })
            .putString(MediaMetadata.METADATA_KEY_ARTIST, i.title)
            .build())
        s.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP)
            .setState(if (i.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
            .build())

        fun action(icon: Int, label: String, act: String) = Notification.Action.Builder(
            Icon.createWithResource(this, icon), label,
            PendingIntent.getService(this, act.hashCode(), Intent(this, ReadAloudService::class.java).setAction(act), PendingIntent.FLAG_IMMUTABLE),
        ).build()

        val open = PendingIntent.getActivity(this, 0, Intent(this, ReaderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(i.label.ifEmpty { i.title })
            .setContentText(i.title)
            .setContentIntent(open)
            .setOngoing(i.playing)
            .addAction(action(android.R.drawable.ic_media_previous, "Previous", ACTION_PREVIOUS))
            .addAction(if (i.playing) action(android.R.drawable.ic_media_pause, "Pause", ACTION_PLAY_PAUSE) else action(android.R.drawable.ic_media_play, "Play", ACTION_PLAY_PAUSE))
            .addAction(action(android.R.drawable.ic_media_next, "Next", ACTION_NEXT))
            .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", ACTION_STOP))
            .setStyle(Notification.MediaStyle().setMediaSession(s.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
        startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        wake?.let { if (i.playing && !it.isHeld) it.acquire(4 * 60 * 60 * 1000L) else if (!i.playing && it.isHeld) it.release() }
    }

    override fun onDestroy() {
        wake?.let { if (it.isHeld) it.release() }
        session?.release()
        super.onDestroy()
    }

    private data class Info(val title: String = "", val label: String = "", val playing: Boolean = true)

    companion object {
        private const val CHANNEL = "read-aloud"
        private const val ID = 7
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_PLAYING = "playing"
        private const val ACTION_PLAY_PAUSE = "play-pause"
        private const val ACTION_NEXT = "next"
        private const val ACTION_PREVIOUS = "previous"
        private const val ACTION_STOP = "stop"
        private var current = Info()

        /** The reader currently speaking; the service forwards controls to it. */
        @Volatile var controls: Controls? = null

        fun update(context: Context, title: String, label: String, playing: Boolean) {
            runCatching {
                context.startForegroundService(Intent(context, ReadAloudService::class.java)
                    .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_LABEL, label).putExtra(EXTRA_PLAYING, playing))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ReadAloudService::class.java))
        }
    }
}
