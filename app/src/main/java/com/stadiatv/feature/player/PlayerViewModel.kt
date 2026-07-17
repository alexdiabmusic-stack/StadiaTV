package com.stadiatv.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.stadiatv.core.player.PlaybackResolver
import com.stadiatv.core.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val loading: Boolean = true,
    val title: String = "",
    val error: String? = null,
    val player: Player? = null,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val resolver: PlaybackResolver,
    private val playerManager: PlayerManager,
) : ViewModel() {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    fun start(mediaId: String) {
        if (_state.value.player != null || mediaId.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val request = resolver.resolve(mediaId)
                request to playerManager.play(request)
            }.onSuccess { (request, player) ->
                _state.update { it.copy(loading = false, title = request.displayTitle, player = player) }
            }.onFailure { err ->
                _state.update { it.copy(loading = false, error = err.message ?: "Stream temporarily unavailable") }
            }
        }
    }

    override fun onCleared() {
        playerManager.release()
        super.onCleared()
    }
}
