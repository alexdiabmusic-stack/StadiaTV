package com.stadiatv.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.stadiatv.core.ui.TvList
import com.stadiatv.core.ui.TvPrimaryButton
import com.stadiatv.core.ui.TvScreen

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(onPlay: (String) -> Unit, onBack: () -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    TvScreen {
        Text("Search")
        BasicTextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            modifier = Modifier.fillMaxWidth().background(Color(0xFF12151A)).padding(16.dp).focusable(),
        )
        TvPrimaryButton("Back", onClick = onBack)
        if (results.isEmpty()) Text("No results")
        TvList(results, key = { it.id }) { item -> TvPrimaryButton(item.name) { onPlay(item.id) } }
    }
}
