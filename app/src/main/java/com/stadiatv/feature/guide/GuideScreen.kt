package com.stadiatv.feature.guide

import androidx.compose.runtime.Composable
import com.stadiatv.core.ui.PlaceholderScreen

@Composable fun GuideScreen(onBack: () -> Unit) = PlaceholderScreen("Programme guide", "EPG data will appear after XMLTV sync. The grid renders a bounded time window.", onBack)
