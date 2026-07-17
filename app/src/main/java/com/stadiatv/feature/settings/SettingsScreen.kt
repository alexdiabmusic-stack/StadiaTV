package com.stadiatv.feature.settings

import androidx.compose.runtime.Composable
import com.stadiatv.core.ui.PlaceholderScreen

@Composable fun SettingsScreen(onBack: () -> Unit) = PlaceholderScreen("Settings", "Playback format, buffering, subtitles, diagnostics, legal, cache, and history settings live here.", onBack)

@Composable fun EpgMappingScreen(onBack: () -> Unit) = PlaceholderScreen("EPG mapping", "Unmatched XMLTV channels can be mapped manually here.", onBack)
