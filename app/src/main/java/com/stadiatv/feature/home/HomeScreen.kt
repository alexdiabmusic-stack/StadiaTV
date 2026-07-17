package com.stadiatv.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.stadiatv.core.ui.MenuRow
import com.stadiatv.core.ui.TvScreen

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(onOpenLive: () -> Unit, onOpenSports: () -> Unit, onOpenSources: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TvScreen {
        Text("StadiaTV")
        MenuRow(listOf("Live channels" to onOpenLive, "Sports hub" to onOpenSports, "Sources" to onOpenSources))
        if (state.live.isEmpty()) {
            Text("No synced channels yet. Demo mode keeps the app browsable while you add a source.")
        } else {
            Text("Live now")
            state.live.take(8).forEach { Text(it.name) }
        }
        if (state.sources.any { it.lastErrorCode != null }) Text("Some sources need attention in Source management.")
    }
}
