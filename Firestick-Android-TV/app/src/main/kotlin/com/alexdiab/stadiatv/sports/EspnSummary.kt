package com.alexdiab.stadiatv.sports

import com.alexdiab.stadiatv.domain.BoxGroup
import com.alexdiab.stadiatv.domain.BoxRow
import com.alexdiab.stadiatv.domain.GameCentre
import com.alexdiab.stadiatv.domain.LeaderComparison
import com.alexdiab.stadiatv.domain.LeaderEntry
import com.alexdiab.stadiatv.domain.LineScore
import com.alexdiab.stadiatv.domain.Match
import com.alexdiab.stadiatv.domain.Play
import com.alexdiab.stadiatv.domain.SportGroup
import com.alexdiab.stadiatv.domain.StatComparison
import com.alexdiab.stadiatv.domain.TeamBox
import com.alexdiab.stadiatv.net.array
import com.alexdiab.stadiatv.net.bool
import com.alexdiab.stadiatv.net.get
import com.alexdiab.stadiatv.net.int
import com.alexdiab.stadiatv.net.isPresent
import com.alexdiab.stadiatv.net.path
import com.alexdiab.stadiatv.net.string
import com.alexdiab.stadiatv.net.stringOrNull
import kotlinx.serialization.json.JsonElement

/** Parses ESPN's `summary` payload into a [GameCentre]. Every sport uses the same envelope with different richness. */
object EspnSummary {
    fun parse(root: JsonElement?, match: Match): GameCentre {
        if (root == null) return GameCentre.EMPTY
        return GameCentre(
            lineScore = lineScore(root, match),
            teamStats = teamStats(root, match),
            leaders = leaders(root, match),
            plays = plays(root, match),
            boxScore = boxScore(root),
            headline = root.path("article", "headline").stringOrNull(),
        )
    }

    // region Line score

    private fun lineScore(root: JsonElement, match: Match): LineScore? {
        val competitors = root.path("header", "competitions", "0", "competitors").array
        if (competitors.isEmpty()) return null
        val away = competitors.firstOrNull { it["homeAway"].string() == "away" } ?: competitors.getOrNull(1)
        val home = competitors.firstOrNull { it["homeAway"].string() == "home" } ?: competitors.getOrNull(0)
        val awayScores = away["linescores"].array.map { it["displayValue"].string("-") }
        val homeScores = home["linescores"].array.map { it["displayValue"].string("-") }
        val count = maxOf(awayScores.size, homeScores.size)
        if (count == 0) return null
        return LineScore(
            periods = periodLabels(match.league.group, count),
            away = awayScores + List(count - awayScores.size) { "-" },
            home = homeScores + List(count - homeScores.size) { "-" },
            awayTotal = away["score"].string(match.away.score.orEmpty()),
            homeTotal = home["score"].string(match.home.score.orEmpty()),
            awayLabel = match.away.abbreviation,
            homeLabel = match.home.abbreviation,
        )
    }

    internal fun periodLabels(group: SportGroup, count: Int): List<String> = List(count) { index ->
        when (group) {
            SportGroup.Hockey -> if (index < 3) "${index + 1}" else if (index == 3) "OT" else "SO"
            SportGroup.Basketball, SportGroup.Football -> if (index < 4) "${index + 1}" else if (index == 4) "OT" else "OT${index - 3}"
            SportGroup.Soccer -> when (index) { 0 -> "1H"; 1 -> "2H"; 2 -> "ET1"; 3 -> "ET2"; else -> "PK" }
            else -> "${index + 1}"
        }
    }

    // endregion

    // region Team stats and leaders

    private fun teamStats(root: JsonElement, match: Match): List<StatComparison> {
        val teams = root.path("boxscore", "teams").array
        if (teams.size < 2) return emptyList()
        val away = teams.firstOrNull { it.path("team", "id").string() == match.away.teamId } ?: teams.firstOrNull { it["homeAway"].string() == "away" } ?: teams[0]
        val home = teams.firstOrNull { it.path("team", "id").string() == match.home.teamId } ?: teams.firstOrNull { it["homeAway"].string() == "home" } ?: teams[1]
        val homeByName = home["statistics"].array.associateBy { it["name"].string() }
        return away["statistics"].array.mapNotNull { stat ->
            val name = stat["name"].string()
            val awayValue = stat["displayValue"].stringOrNull() ?: return@mapNotNull null
            val homeValue = homeByName[name]?.get("displayValue").stringOrNull() ?: return@mapNotNull null
            val label = stat["label"].stringOrNull() ?: stat["displayName"].stringOrNull() ?: name.replaceFirstChar(Char::uppercase)
            StatComparison(label, awayValue, homeValue)
        }.distinctBy { it.label }.take(14)
    }

    private fun leaders(root: JsonElement, match: Match): List<LeaderComparison> {
        val teams = root["leaders"].array
        if (teams.isEmpty()) return emptyList()
        fun entries(side: JsonElement?): Map<String, LeaderEntry> {
            val out = LinkedHashMap<String, LeaderEntry>()
            side["leaders"].array.forEach { category ->
                val top = category["leaders"][0] ?: return@forEach
                val athlete = top["athlete"]
                val name = athlete["displayName"].stringOrNull() ?: return@forEach
                val label = category["displayName"].stringOrNull() ?: category["name"].string().replaceFirstChar(Char::uppercase)
                out[label] = LeaderEntry(
                    name = name,
                    value = top["displayValue"].string(),
                    detail = top["summary"].stringOrNull() ?: athlete.path("position", "abbreviation").string(),
                    headshotUrl = athlete.path("headshot", "href").stringOrNull(),
                )
            }
            return out
        }
        val away = entries(teams.firstOrNull { it.path("team", "id").string() == match.away.teamId } ?: teams.getOrNull(0))
        val home = entries(teams.firstOrNull { it.path("team", "id").string() == match.home.teamId } ?: teams.getOrNull(1))
        return (away.keys + home.keys).distinct().take(6).map { LeaderComparison(it, away[it], home[it]) }
    }

    // endregion

    // region Plays

    private fun plays(root: JsonElement, match: Match): List<Play> {
        val raw = root["plays"].array.ifEmpty { root["keyEvents"].array }
        if (raw.isEmpty()) return emptyList()
        return raw.mapIndexedNotNull { index, play ->
            val text = play["text"].stringOrNull() ?: play["shortText"].stringOrNull() ?: play.path("type", "text").stringOrNull() ?: return@mapIndexedNotNull null
            Play(
                id = play["id"].stringOrNull() ?: "play-$index",
                period = periodText(play, match),
                clock = play.path("clock", "displayValue").string(),
                text = text,
                teamId = play.path("team", "id").stringOrNull(),
                awayScore = play["awayScore"].stringOrNull(),
                homeScore = play["homeScore"].stringOrNull(),
                isScoring = play["scoringPlay"].bool() || play["scoreValue"].int() > 0,
                type = play.path("type", "text").string(),
            )
        }.takeLast(80).reversed()
    }

    private fun periodText(play: JsonElement, match: Match): String {
        val number = play.path("period", "number").int(0)
        if (number <= 0) return play.path("period", "displayValue").string()
        return periodLabels(match.league.group, number).last().let { label -> if (match.league.group == SportGroup.Baseball) "Inning $number" else label }
    }

    // endregion

    // region Box score

    private fun boxScore(root: JsonElement): List<TeamBox> =
        root.path("boxscore", "players").array.mapNotNull { team ->
            val groups = team["statistics"].array.mapNotNull { group ->
                val labels = group["labels"].array.map { it.string() }
                val rows = group["athletes"].array.mapNotNull { entry ->
                    val athlete = entry["athlete"]
                    val name = athlete["displayName"].stringOrNull() ?: return@mapNotNull null
                    BoxRow(name, athlete.path("position", "abbreviation").string(), entry["stats"].array.map { it.string() })
                }
                if (rows.isEmpty()) return@mapNotNull null
                val title = group["text"].stringOrNull() ?: group["name"].string().replaceFirstChar(Char::uppercase)
                BoxGroup(title, labels, rows, group["totals"].array.map { it.string() })
            }
            if (groups.isEmpty()) null
            else TeamBox(team.path("team", "displayName").string("Team"), team.path("team", "abbreviation").string(), groups)
        }

    // endregion
}
