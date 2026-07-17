package com.stadiatv.feature.sources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.stadiatv.core.ui.PlaceholderScreen
import com.stadiatv.core.ui.TvPrimaryButton
import com.stadiatv.core.ui.TvScreen
import com.stadiatv.feature.home.HomeViewModel

@Composable
fun SourcesScreen(onBack: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TvScreen {
        Text("Source management")
        TvPrimaryButton("Back", onClick = onBack)
        if (state.sources.isEmpty()) Text("No saved sources.")
        state.sources.forEach {
            Text("${it.displayName} · ${it.type} · ${it.status} · last success ${it.lastSuccessfulSyncAt ?: "never"}")
        }
    }
}
