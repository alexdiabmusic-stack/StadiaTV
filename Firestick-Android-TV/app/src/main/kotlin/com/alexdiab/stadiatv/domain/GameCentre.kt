package com.alexdiab.stadiatv.domain

import androidx.compose.runtime.Immutable

/** Everything the Match screen shows beyond the scoreboard. Sections a provider can't supply are simply empty. */
@Immutable
data class GameCentre(
    val lineScore: LineScore? = null,
    val teamStats: List<StatComparison> = emptyList(),
    val leaders: List<LeaderComparison> = emptyList(),
    /** Newest first. */
    val plays: List<Play> = emptyList(),
    val boxScore: List<TeamBox> = emptyList(),
    val headline: String? = null,
) {
    val isEmpty: Boolean get() = lineScore == null && teamStats.isEmpty() && leaders.isEmpty() && plays.isEmpty() && boxScore.isEmpty()

    companion object { val EMPTY = GameCentre() }
}

@Immutable
data class LineScore(
    val periods: List<String>,
    val away: List<String>,
    val home: List<String>,
    val awayTotal: String,
    val homeTotal: String,
    val awayLabel: String,
    val homeLabel: String,
)

/** One row of a head-to-head team stat table ("Shots on goal", 24 vs 31). */
@Immutable
data class StatComparison(val label: String, val away: String, val home: String) {
    /** Share of the bar given to the away side, when both values are numeric. */
    val awayShare: Float?
        get() {
            val a = away.toNumber() ?: return null
            val h = home.toNumber() ?: return null
            val total = a + h
            return if (total <= 0.0) 0.5f else (a / total).toFloat().coerceIn(0f, 1f)
        }

    private fun String.toNumber(): Double? = replace("%", "").replace(",", "").trim().toDoubleOrNull()
}

@Immutable
data class LeaderEntry(val name: String, val value: String, val detail: String, val headshotUrl: String?)

@Immutable
data class LeaderComparison(val category: String, val away: LeaderEntry?, val home: LeaderEntry?)

@Immutable
data class Play(
    val id: String,
    val period: String,
    val clock: String,
    val text: String,
    val teamId: String?,
    val awayScore: String?,
    val homeScore: String?,
    val isScoring: Boolean,
    val type: String = "",
)

@Immutable
data class BoxRow(val name: String, val detail: String, val stats: List<String>)

@Immutable
data class BoxGroup(val title: String, val labels: List<String>, val rows: List<BoxRow>, val totals: List<String>)

@Immutable
data class TeamBox(val teamName: String, val abbreviation: String, val groups: List<BoxGroup>)
