package com.stadiatv.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stadiatv.core.model.MediaItem
import com.stadiatv.core.sync.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val repository: PlaylistRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val results = query.flatMapLatest { q ->
        if (q.isBlank()) repository.observeLiveChannels(30) else repository.searchChannels(q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<MediaItem>())
}
