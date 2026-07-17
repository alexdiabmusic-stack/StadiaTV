package com.stadiatv.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stadiatv.core.sync.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LiveViewModel @Inject constructor(repository: PlaylistRepository) : ViewModel() {
    val live = repository.observeLiveChannels(500).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
