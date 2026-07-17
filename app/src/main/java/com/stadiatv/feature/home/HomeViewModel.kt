package com.stadiatv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stadiatv.core.model.MediaItem
import com.stadiatv.core.model.PlaylistSource
import com.stadiatv.core.sync.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(val sources: List<PlaylistSource> = emptyList(), val live: List<MediaItem> = emptyList())

@HiltViewModel
class HomeViewModel @Inject constructor(repository: PlaylistRepository) : ViewModel() {
    val state = combine(repository.observeSources(), repository.observeLiveChannels(20)) { sources, live ->
        HomeUiState(sources, live)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
