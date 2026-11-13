package com.matuucrea.miltvplus.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.matuucrea.miltvplus.data.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class PlayerViewModel(application: Application): AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("miltvplus_library", 0)
    private val _channels = MutableStateFlow(loadChannels())
    val channels = _channels.asStateFlow()
    private val _selected = MutableStateFlow<Channel?>(null)
    val selected = _selected.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _favorites = MutableStateFlow(loadFavorites())
    val favorites = _favorites.asStateFlow()

    private fun loadFavorites(): Set<String> = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
    private fun loadChannels(): List<Channel> = runCatching {
        val array = JSONArray(prefs.getString("channels", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Channel(o.getString("id"), o.getString("name"), o.getString("url"), o.optString("group"), o.optString("logo").takeIf { it.isNotBlank() })
        }
    }.getOrDefault(emptyList())

    fun setChannels(v: List<Channel>) {
        _channels.value = v
        val array = JSONArray()
        v.forEach { c -> array.put(JSONObject().put("id", c.id).put("name", c.name).put("url", c.url).put("group", c.group).put("logo", c.logo ?: "")) }
        prefs.edit().putString("channels", array.toString()).apply()
        _error.value = null
    }
    fun play(c: Channel) {
        if (!c.url.startsWith("https://", true)) { _error.value = "Por seguridad, este reproductor solo acepta fuentes HTTPS."; return }
        _error.value = null
        _selected.value = c
    }
    fun stop() { _selected.value = null; _error.value = null }
    fun setError(v: String) { _error.value = v }
    fun toggleFavorite(id: String) {
        val next = if (id in _favorites.value) _favorites.value - id else _favorites.value + id
        _favorites.value = next
        prefs.edit().putStringSet("favorites", next).apply()
    }
}
