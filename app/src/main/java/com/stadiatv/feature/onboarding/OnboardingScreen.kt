package com.stadiatv.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.stadiatv.core.model.SourceType
import com.stadiatv.core.ui.TvPrimaryButton
import com.stadiatv.core.ui.TvScreen

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TvScreen {
        Text("Add your IPTV source")
        Text("Only add playlists or services you are authorized to access and play.")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { viewModel.update { copy(mode = SourceType.M3U) } }) { Text("Add M3U playlist") }
            Button(onClick = { viewModel.update { copy(mode = SourceType.XTREAM) } }) { Text("Add Xtream account") }
            Button(onClick = onDone) { Text("Open demo mode") }
        }
        TvField("Display name", state.displayName) { viewModel.update { copy(displayName = it) } }
        if (state.mode == SourceType.M3U) {
            TvField("Playlist URL", state.playlistUrl) { viewModel.update { copy(playlistUrl = it) } }
            TvField("Optional XMLTV URL", state.epgUrl) { viewModel.update { copy(epgUrl = it) } }
        } else {
            TvField("Server URL", state.serverUrl) { viewModel.update { copy(serverUrl = it) } }
            TvField("Username", state.username) { viewModel.update { copy(username = it) } }
            TvField("Password", state.password) { viewModel.update { copy(password = it) } }
        }
        Button(onClick = { viewModel.update { copy(authorized = !authorized) } }) {
            Text(if (state.authorized) "Authorization confirmed" else "Confirm authorization")
        }
        state.error?.let { Text(it) }
        TvPrimaryButton(if (state.saving) "Saving..." else "Save and sync") { viewModel.saveAndSync(onDone) }
    }
}

@Composable
private fun TvField(label: String, value: String, onValue: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF12151A))
                .padding(16.dp)
                .focusable(),
        )
    }
}
