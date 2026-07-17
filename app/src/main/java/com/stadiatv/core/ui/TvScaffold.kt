package com.stadiatv.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

val TvBackground = Color(0xFF06070A)

@Composable
fun TvScreen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 40.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        content = content,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.focusable()) { Text(label) }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MenuRow(actions: List<Pair<String, () -> Unit>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        actions.forEach { (label, action) -> TvPrimaryButton(label, onClick = action) }
    }
}

@Composable
fun PlaceholderScreen(title: String, body: String, onBack: () -> Unit) {
    TvScreen {
        Text(title)
        Text(body)
        TvPrimaryButton("Back", onClick = onBack)
    }
}

@Composable
fun <T> TvList(items: List<T>, key: (T) -> Any, row: @Composable (T) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items = items, key = key) { row(it) }
    }
}
