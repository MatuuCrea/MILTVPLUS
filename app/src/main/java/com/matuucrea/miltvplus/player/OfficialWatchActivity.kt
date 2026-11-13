package com.matuucrea.miltvplus.player

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONArray
import org.json.JSONObject
import com.matuucrea.miltvplus.data.SupabaseCatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Full-screen official broadcaster page. The broadcaster retains control of its own player and territorial rules. */
class OfficialWatchActivity : Activity() {
    companion object {
        const val EXTRA_CHANNELS = "miltvplus.official.channels"
        const val EXTRA_START_INDEX = "miltvplus.official.start"
        const val EXTRA_REMOTE_CATALOG = "miltvplus.official.remote"
        const val EXTRA_COUNTRY_CODE = "miltvplus.official.country"
        const val EXTRA_CATEGORY = "miltvplus.official.category"
    }
    private lateinit var root: LinearLayout
    private lateinit var webView: WebView
    private lateinit var title: TextView
    private lateinit var controls: LinearLayout
    private lateinit var previous: Button
    private lateinit var next: Button
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private val channels = ArrayList<JSONObject>()
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        val raw = runCatching { JSONArray(intent.getStringExtra(EXTRA_CHANNELS) ?: "[]") }.getOrElse { JSONArray() }
        for (i in 0 until raw.length()) raw.optJSONObject(i)?.let { row -> if (isSafeHttps(row.optString("url"))) channels.add(row) }
        if (channels.isEmpty()) { finish(); return }
        index = intent.getIntExtra(EXTRA_START_INDEX, 0).coerceIn(0, channels.lastIndex)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(android.graphics.Color.BLACK) }
        title = TextView(this).apply { setTextColor(android.graphics.Color.WHITE); textSize = 16f; setPadding(16, 10, 16, 10) }
        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (android.os.Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.scheme !in setOf("https", "http")
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) { handler.cancel() }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (customView != null) { callback.onCustomViewHidden(); return }
                    customView = view
                    customViewCallback = callback
                    webView.visibility = View.GONE
                    title.visibility = View.GONE
                    controls.visibility = View.GONE
                    root.addView(view, 0, LinearLayout.LayoutParams(-1, 0, 1f))
                    hideSystemBars()
                }
                override fun onHideCustomView() { restoreWebViewFromCustomView() }
            }
        }
        controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setBackgroundColor(0xFF101010.toInt()) }
        previous = Button(this).apply { text = "◀ Canal anterior" }
        next = Button(this).apply { text = "Canal siguiente ▶" }
        val external = Button(this).apply { text = "Abrir en navegador" }
        val close = Button(this).apply { text = "Cerrar" }
        controls.addView(previous, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(next, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(external, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(close, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(controls, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        previous.setOnClickListener { loadChannel(index - 1) }
        next.setOnClickListener { loadChannel(index + 1) }
        external.setOnClickListener {
            val url = channels[index].optString("url")
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
        close.setOnClickListener { finish() }
        loadChannel(index)
        if (intent.getBooleanExtra(EXTRA_REMOTE_CATALOG, false)) {
            val selectedCountry = intent.getStringExtra(EXTRA_COUNTRY_CODE).orEmpty().takeIf { it.matches(Regex("^[A-Z]{2}$")) }
            val selectedCategory = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
            val repository = SupabaseCatalogRepository()
            refreshScope.launch {
                while (isActive) {
                    delay(60_000)
                    val fresh = repository.fetchPublished("tv_channels", null).getOrNull() ?: continue
                    val eligible = fresh.filter { entry ->
                        entry.infoUrl != null &&
                            (selectedCountry == null || entry.countryCode == selectedCountry) &&
                            (selectedCategory.isBlank() || entry.category == selectedCategory)
                    }
                    if (eligible.isEmpty()) continue
                    val currentId = channels.getOrNull(index)?.optString("id")
                    val oldUrls = channels.map { it.optString("url") }
                    val newRows = eligible.map { entry -> JSONObject().put("id", entry.id).put("name", entry.title).put("url", entry.infoUrl) }
                    val newUrls = newRows.map { it.optString("url") }
                    val newIndex = newRows.indexOfFirst { it.optString("id") == currentId }.let { if (it >= 0) it else 0 }
                    channels.clear()
                    channels.addAll(newRows)
                    index = newIndex
                    if (oldUrls != newUrls) loadChannel(index)
                }
            }
        }
    }

    private fun restoreWebViewFromCustomView() {
        customView?.let { root.removeView(it) }
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        webView.visibility = View.VISIBLE
        title.visibility = View.VISIBLE
        controls.visibility = View.VISIBLE
        hideSystemBars()
    }

    private fun loadChannel(newIndex: Int) {
        index = (newIndex + channels.size) % channels.size
        val channel = channels[index]
        title.text = channel.optString("name", "Canal oficial")
        previous.isEnabled = channels.size > 1
        next.isEnabled = channels.size > 1
        webView.loadUrl(channel.optString("url"))
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideSystemBars() }
    @Deprecated("Deprecated in Android API; retained for WebView back navigation")
    override fun onBackPressed() {
        if (customView != null) { restoreWebViewFromCustomView(); return }
        if (webView.canGoBack()) webView.goBack() else finish()
    }

    override fun onDestroy() {
        refreshScope.cancel()
        customViewCallback?.onCustomViewHidden()
        webView.stopLoading()
        webView.setWebChromeClient(null)
        webView.setWebViewClient(WebViewClient())
        webView.destroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    private fun isSafeHttps(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
