package com.stadiatv.feature.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.stadiatv.core.ui.TvPrimaryButton
import com.stadiatv.core.ui.TvScreen

@Composable
fun PlayerScreen(mediaId: String, onBack: () -> Unit, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(mediaId) { viewModel.start(mediaId) }
    if (state.player != null) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> PlayerView(context).apply { player = state.player } },
            update = { it.player = state.player },
        )
    } else {
        TvScreen {
            Text(if (state.loading) "Loading stream" else "Playback error")
            state.error?.let { Text(it) }
            TvPrimaryButton("Back", onClick = onBack)
        }
    }
}
