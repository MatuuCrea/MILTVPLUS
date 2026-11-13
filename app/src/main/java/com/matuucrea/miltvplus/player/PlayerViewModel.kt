package com.matuucrea.miltvplus.player
import androidx.lifecycle.ViewModel
import com.matuucrea.miltvplus.data.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
class PlayerViewModel:ViewModel() {
    private val _channels=MutableStateFlow<List<Channel>>(emptyList()); val channels=_channels.asStateFlow()
    private val _selected=MutableStateFlow<Channel?>(null); val selected=_selected.asStateFlow()
    private val _error=MutableStateFlow<String?>(null); val error=_error.asStateFlow()
    private val _favorites=MutableStateFlow<Set<String>>(emptySet()); val favorites=_favorites.asStateFlow()
    fun setChannels(v:List<Channel>){_channels.value=v;_error.value=null}
    fun play(c:Channel){if(!c.url.startsWith("https://")){_error.value="Se requiere HTTPS por seguridad.";return};_error.value=null;_selected.value=c}
    fun stop(){_selected.value=null;_error.value=null}
    fun setError(v:String){_error.value=v}
    fun toggleFavorite(id:String){_favorites.value=if(id in _favorites.value)_favorites.value-id else _favorites.value+id}
}
