package com.stadiatv.feature.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.stadiatv.core.ui.TvList
import com.stadiatv.core.ui.TvPrimaryButton
import com.stadiatv.core.ui.TvScreen

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveScreen(onPlay: (String) -> Unit, onBack: () -> Unit, viewModel: LiveViewModel = hiltViewModel()) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    TvScreen {
        Text("Live channels")
        TvPrimaryButton("Back", onClick = onBack)
        if (live.isEmpty()) Text("No live channels are available. Sync a source first.")
        TvList(live, key = { it.id }) { channel ->
            TvPrimaryButton(channel.name) { onPlay(channel.id) }
        }
    }
}
