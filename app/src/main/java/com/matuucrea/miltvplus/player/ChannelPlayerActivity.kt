package com.matuucrea.miltvplus.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.json.JSONArray

/** Immersive adaptive live player for explicitly authorized HTTPS streams. */
class ChannelPlayerActivity : Activity() {
    companion object {
        const val EXTRA_CHANNELS = "miltvplus.player.channels"
        const val EXTRA_START_INDEX = "miltvplus.player.start_index"
    }
    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var retryCount = 0
    private lateinit var title: TextView
    private lateinit var previous: Button
    private lateinit var next: Button
    private val retryRunnable = Runnable {
        player?.let {
            if (it.playbackState == Player.STATE_IDLE || it.playbackState == Player.STATE_BUFFERING) {
                it.prepare()
                it.playWhenReady = true
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val channels = runCatching { JSONArray(intent.getStringExtra(EXTRA_CHANNELS) ?: "[]") }.getOrElse { JSONArray() }
        val items = ArrayList<MediaItem>()
        val names = ArrayList<String>()
        for (i in 0 until channels.length()) {
            val row = channels.optJSONObject(i) ?: continue
            val url = row.optString("url")
            if (!isSafeHttps(url)) continue
            val cleanUrl = url.substringBefore("?")
            val mime = when {
                cleanUrl.endsWith(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
                cleanUrl.endsWith(".mpd", true) -> MimeTypes.APPLICATION_MPD
                else -> null
            }
            val media = MediaItem.Builder().setUri(url).apply { if (mime != null) setMimeType(mime) }.build()
            items.add(media)
            names.add(row.optString("name", "Canal"))
        }
        if (items.isEmpty()) { finish(); return }
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0).coerceIn(0, items.lastIndex)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(android.graphics.Color.BLACK) }
        title = TextView(this).apply { setTextColor(android.graphics.Color.WHITE); textSize = 16f; setPadding(18, 8, 18, 8); text = names[startIndex] }
        val video = PlayerView(this).apply {
            useController = true
            controllerAutoShow = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            keepScreenOn = true
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setBackgroundColor(0xFF101010.toInt()) }
        previous = Button(this).apply { text = "◀ Canal anterior"; isFocusable = true }
        next = Button(this).apply { text = "Canal siguiente ▶"; isFocusable = true }
        val close = Button(this).apply { text = "Salir de pantalla completa"; isFocusable = true }
        controls.addView(previous, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(next, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(close, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(video, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(controls, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 1_500, 3_000).build()
        val exo = ExoPlayer.Builder(this).setLoadControl(loadControl).build()
        player = exo
        video.player = exo
        exo.setMediaItems(items, startIndex, 0L)
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                retryCount = 0
                title.text = names.getOrElse(exo.currentMediaItemIndex) { "MILTVPLUS" }
                previous.isEnabled = exo.hasPreviousMediaItem()
                next.isEnabled = exo.hasNextMediaItem()
            }
            override fun onPlayerError(error: PlaybackException) {
                if (retryCount >= 3) { title.text = "No se pudo reproducir · " + names.getOrElse(exo.currentMediaItemIndex) { "Canal" }; return }
                retryCount++
                title.text = "Reconectando (" + retryCount + "/3)…"
                handler.removeCallbacks(retryRunnable)
                handler.postDelayed(retryRunnable, 1_000L * retryCount)
            }
        })
        previous.setOnClickListener { if (exo.hasPreviousMediaItem()) exo.seekToPreviousMediaItem() }
        next.setOnClickListener { if (exo.hasNextMediaItem()) exo.seekToNextMediaItem() }
        close.setOnClickListener { finish() }
        exo.prepare()
        exo.playWhenReady = true
        previous.isEnabled = exo.hasPreviousMediaItem()
        next.isEnabled = exo.hasNextMediaItem()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
    private fun isSafeHttps(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
