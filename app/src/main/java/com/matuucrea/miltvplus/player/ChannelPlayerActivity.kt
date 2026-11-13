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
import com.matuucrea.miltvplus.data.SupabaseCatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Immersive adaptive live player for explicitly authorized HTTPS streams. */
class ChannelPlayerActivity : Activity() {
    companion object {
        const val EXTRA_CHANNELS = "miltvplus.player.channels"
        const val EXTRA_START_INDEX = "miltvplus.player.start_index"
        const val EXTRA_REMOTE_CATALOG = "miltvplus.player.remote_catalog"
        const val EXTRA_COUNTRY_CODE = "miltvplus.player.country_code"
        const val EXTRA_CATEGORY = "miltvplus.player.category"
    }
    private val handler = Handler(Looper.getMainLooper())
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var retryCount = 0
    private lateinit var title: TextView
    private lateinit var previous: Button
    private lateinit var next: Button
    private val retryRunnable = Runnable {
        player?.let {
            if (it.playbackState == Player.STATE_IDLE || it.playbackState == Player.STATE_BUFFERING) {
                it.seekToDefaultPosition()
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
        val activeIds = ArrayList<String>()
        val activeUrls = ArrayList<String>()
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
            activeIds.add(row.optString("id").ifBlank { url })
            activeUrls.add(url)
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
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    retryCount = 0
                    title.text = names.getOrElse(exo.currentMediaItemIndex) { "MILTVPLUS" }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                retryCount++
                val delayMs = (1_000L shl (retryCount - 1).coerceIn(0, 5)).coerceAtMost(30_000L)
                title.text = "Reconectando (intento $retryCount)…"
                handler.removeCallbacks(retryRunnable)
                handler.postDelayed(retryRunnable, delayMs)
            }
        })
        previous.setOnClickListener { if (exo.hasPreviousMediaItem()) exo.seekToPreviousMediaItem() }
        next.setOnClickListener { if (exo.hasNextMediaItem()) exo.seekToNextMediaItem() }
        close.setOnClickListener { finish() }
        exo.prepare()
        exo.playWhenReady = true
        previous.isEnabled = exo.hasPreviousMediaItem()
        next.isEnabled = exo.hasNextMediaItem()

        if (intent.getBooleanExtra(EXTRA_REMOTE_CATALOG, false)) {
            val selectedCountry = intent.getStringExtra(EXTRA_COUNTRY_CODE).orEmpty().takeIf { it.matches(Regex("^[A-Z]{2}$")) }
            val selectedCategory = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
            val repository = SupabaseCatalogRepository()
            refreshScope.launch {
                while (isActive) {
                    delay(60_000)
                    val refreshed = repository.fetchPublished("tv_channels", null).getOrNull() ?: continue
                    val eligible = refreshed.filter { entry ->
                        entry.actionUrl?.let(::isSafeHttps) == true &&
                            (selectedCountry == null || entry.countryCode == selectedCountry) &&
                            (selectedCategory.isBlank() || entry.category == selectedCategory)
                    }
                    val urls = eligible.mapNotNull { it.actionUrl }
                    val ids = eligible.map { it.id }
                    if (urls.isEmpty()) continue
                    if (urls == activeUrls && ids == activeIds) {
                        names.clear()
                        names.addAll(eligible.map { it.title })
                        title.text = names.getOrElse(exo.currentMediaItemIndex) { "MILTVPLUS" }
                        continue
                    }
                    val currentIndex = exo.currentMediaItemIndex
                    val currentId = activeIds.getOrNull(currentIndex)
                    val newItems = urls.map { streamUrl ->
                        val clean = streamUrl.substringBefore("?")
                        val mime = when {
                            clean.endsWith(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
                            clean.endsWith(".mpd", true) -> MimeTypes.APPLICATION_MPD
                            else -> null
                        }
                        MediaItem.Builder().setUri(streamUrl).apply { if (mime != null) setMimeType(mime) }.build()
                    }
                    names.clear()
                    names.addAll(eligible.map { it.title })
                    activeIds.clear()
                    activeIds.addAll(ids)
                    activeUrls.clear()
                    activeUrls.addAll(urls)
                    val newIndex = currentId?.let { ids.indexOf(it) } ?: -1
                    exo.setMediaItems(newItems, newIndex.coerceAtLeast(0), 0L)
                    exo.prepare()
                    exo.playWhenReady = true
                }
            }
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        refreshScope.cancel()
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
