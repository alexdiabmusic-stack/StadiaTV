package com.stadiatv.feature.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stadiatv.core.sports.EspnSportsRepository
import com.stadiatv.core.sports.SportsHubData
import com.stadiatv.core.sports.StadiaSportsFeeds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SportsUiState(
    val tab: String = "live",
    val selectedSport: String = "all",
    val favoriteSports: List<String> = listOf("nfl", "nba", "mlb", "nhl", "college-football"),
    val dateOffset: Long = 0,
    val loading: Boolean = false,
    val data: SportsHubData = SportsHubData(emptyList(), emptyList(), emptyList(), emptyList()),
)

@HiltViewModel
class SportsViewModel @Inject constructor(
    private val repository: EspnSportsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SportsUiState())
    val state: StateFlow<SportsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun selectTab(tab: String) {
        _state.update { it.copy(tab = tab) }
    }

    fun selectSport(id: String) {
        _state.update {
            val favorites = if (id != "all" && id !in it.favoriteSports) it.favoriteSports + id else it.favoriteSports
            it.copy(selectedSport = id, favoriteSports = favorites)
        }
        refresh()
    }

    fun moveDate(delta: Long) {
        _state.update { it.copy(dateOffset = it.dateOffset + delta) }
        refresh()
    }

    fun refresh() {
        val snapshot = _state.value
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val feeds = snapshot.favoriteSports.ifEmpty { StadiaSportsFeeds.all.take(5).map { it.id } }
            val date = LocalDate.now().plusDays(snapshot.dateOffset)
            val data = repository.hydrate(feeds, date)
            _state.update { it.copy(loading = false, data = data) }
        }
    }
}
