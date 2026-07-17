package com.stadiatv.feature.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.stadiatv.core.sports.SportsMatch
import com.stadiatv.core.sports.SportsNews
import com.stadiatv.core.sports.StadiaSportsFeeds
import com.stadiatv.core.ui.TvPrimaryButton
import com.stadiatv.core.ui.TvScreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val Card = Color(0xFF12151A)
private val CardFlat = Color(0xFF0B0D10)
private val Border = Color(0xFF20262F)
private val Accent = Color(0xFF2F81F7)
private val Accent2 = Color(0xFF57A2FF)
private val Green = Color(0xFF34D17A)
private val Muted = Color(0xFF8A94A3)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsScreen(onBack: () -> Unit, viewModel: SportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val matches = state.data.matches
    val live = matches.count { it.status == "live" }
    val upcoming = matches.count { it.status == "upcoming" }
    val dateLabel = LocalDate.now().plusDays(state.dateOffset).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val visibleMatches = matches.filter {
        (state.tab == "live" && it.status == "live" || state.tab == "upcoming" && it.status == "upcoming") &&
            (state.selectedSport == "all" || it.sport == state.selectedSport)
    }
    val grouped = visibleMatches.groupBy { it.league }
    val news = state.data.news.filter { state.selectedSport == "all" || it.sport == state.selectedSport }

    TvScreen {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
            SportsRail(onBack)
            Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(if (state.loading) "Refreshing ESPN feeds..." else "Scores, news, and future games", color = Muted, fontSize = 16.sp)
                        Text("SPORTS HUB", fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Segment("Live $live", state.tab == "live") { viewModel.selectTab("live") }
                        Segment("Upcoming $upcoming", state.tab == "upcoming") { viewModel.selectTab("upcoming") }
                        Segment("News ${state.data.news.size}", state.tab == "news") { viewModel.selectTab("news") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvPrimaryButton("Prev") { viewModel.moveDate(-1) }
                    Pill(dateLabel)
                    TvPrimaryButton("Next") { viewModel.moveDate(1) }
                    TvPrimaryButton(if (state.loading) "Refreshing..." else "Refresh scores") { viewModel.refresh() }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { SportChip("All Sports", state.selectedSport == "all") { viewModel.selectSport("all") } }
                    items(StadiaSportsFeeds.all, key = { it.id }) { feed ->
                        SportChip(feed.name, state.selectedSport == feed.id) { viewModel.selectSport(feed.id) }
                    }
                }
                if (state.tab == "news") {
                    NewsList(news)
                } else if (grouped.isEmpty()) {
                    EmptyState("No matches here right now", "Try another sport, date, or tab.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        grouped.forEach { (league, leagueMatches) ->
                            item(key = league) {
                                LeagueGroup(league, leagueMatches)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsRail(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(236.dp)
            .background(Color(0xFF0C0E12), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).background(Accent, RoundedCornerShape(10.dp)))
            Text("STADIATV", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
        Pill("Home")
        Pill("Live TV")
        Pill("Sports", active = true)
        Pill("Search")
        Spacer(Modifier.weight(1f))
        TvPrimaryButton("Back") { onBack() }
    }
}

@Composable
private fun Segment(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) Accent else CardFlat)
            .border(1.dp, if (active) Accent else Border, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 20.dp, vertical = 11.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold, color = if (active) Color.White else Muted)
    }
}

@Composable
private fun SportChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) Accent else Card)
            .border(1.dp, if (active) Accent else Border, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold, color = if (active) Color.White else Color(0xFFC3CCD6))
    }
}

@Composable
private fun Pill(label: String, active: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Color(0x292F81F7) else Card)
            .border(1.dp, if (active) Accent else Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, color = if (active) Accent2 else Muted, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LeagueGroup(league: String, matches: List<SportsMatch>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(width = 4.dp, height = 22.dp).background(Accent, RoundedCornerShape(4.dp)))
            Text(league, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Pill("${matches.size}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            matches.take(3).forEach { MatchCard(it) }
        }
    }
}

@Composable
private fun MatchCard(match: SportsMatch) {
    Column(
        modifier = Modifier
            .size(width = 320.dp, height = 190.dp)
            .background(Card, RoundedCornerShape(16.dp))
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(18.dp)
            .focusable(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(match.league, color = Muted, fontSize = 14.sp)
            Pill(if (match.status == "live") match.clock.ifBlank { "LIVE" } else match.startLabel)
        }
        TeamRow(match.home, match.homeScore, match.status != "upcoming")
        TeamRow(match.away, match.awayScore, match.status != "upcoming")
    }
}

@Composable
private fun TeamRow(team: com.stadiatv.core.sports.SportsTeam, score: Int, showScore: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(team.color), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(team.abbr, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(team.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        if (showScore) Text("$score", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun NewsList(news: List<SportsNews>) {
    if (news.isEmpty()) {
        EmptyState("No news loaded", "Refresh scores or select another sport.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(news, key = { it.id }) { article ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Card, RoundedCornerShape(16.dp))
                    .border(1.dp, Border, RoundedCornerShape(16.dp))
                    .padding(18.dp)
                    .focusable(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(article.league, color = Muted, fontSize = 12.sp)
                Text(article.headline, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(article.description, color = Muted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 80.dp)) {
        Text(title, color = Muted, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(body, color = Muted, fontSize = 16.sp)
    }
}
