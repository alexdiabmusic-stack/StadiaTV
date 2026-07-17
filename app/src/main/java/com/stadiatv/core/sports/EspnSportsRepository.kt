package com.stadiatv.core.sports

import com.stadiatv.core.network.ProviderHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EspnSportsRepository @Inject constructor(
    private val httpClient: ProviderHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = mutableMapOf<String, Pair<Long, JsonElement>>()

    suspend fun hydrate(feedIds: List<String>, date: LocalDate): SportsHubData = coroutineScope {
        val ids = feedIds.ifEmpty { StadiaSportsFeeds.all.take(5).map { it.id } }
        val results = ids.map { id ->
            async {
                runCatching {
                    val feed = StadiaSportsFeeds.byId(id)
                    val scores = fetchScores(feed, date)
                    val news = if (feed.hasNews) fetchNews(feed) else emptyList()
                    val teams = if (feed.hasTeams) fetchTeams(feed) else emptyList()
                    SportsHubData(scores, teams, news, emptyList())
                }.getOrElse { SportsHubData(emptyList(), emptyList(), emptyList(), listOf(it.message ?: "Sports feed failed")) }
            }
        }.awaitAll()
        SportsHubData(
            matches = results.flatMap { it.matches },
            teams = results.flatMap { it.teams },
            news = results.flatMap { it.news },
            errors = results.flatMap { it.errors },
        )
    }

    private suspend fun fetchScores(feed: SportsFeed, date: LocalDate): List<SportsMatch> {
        val params = mutableMapOf("dates" to date.format(DateTimeFormatter.BASIC_ISO_DATE))
        if (feed.id == "college-football") params["calendar"] = "blacklist"
        var root = getJson(endpoint(feed, "scoreboard", params)).jsonObject
        if (root.array("events").isEmpty() && date == LocalDate.now()) {
            root = getJson(endpoint(feed, "scoreboard")).jsonObject
        }
        return root.array("events").mapNotNull { event -> event.asObjectOrNull()?.toMatch(feed) }
    }

    private suspend fun fetchNews(feed: SportsFeed): List<SportsNews> {
        val root = getJson(endpoint(feed, "news")).jsonObject
        return root.array("articles").take(8).map { article ->
            val obj = article.jsonObject
            SportsNews(
                id = obj.string("id") ?: obj.string("headline").orEmpty(),
                sport = feed.id,
                league = feed.name,
                headline = obj.string("headline").orEmpty().ifBlank { "Untitled story" },
                description = obj.string("description").orEmpty(),
                imageUrl = obj.array("images").firstOrNull()?.asObjectOrNull()?.string("url"),
                webUrl = obj.obj("links")?.obj("web")?.string("href"),
            )
        }
    }

    private suspend fun fetchTeams(feed: SportsFeed): List<SportsTeam> {
        val root = getJson(endpoint(feed, "teams")).jsonObject
        val teams = root.array("sports").firstOrNull()?.asObjectOrNull()
            ?.array("leagues")?.firstOrNull()?.asObjectOrNull()
            ?.array("teams")
            .orEmpty()
        return teams.mapNotNull { entry ->
            val team = entry.asObjectOrNull()?.obj("team") ?: entry.asObjectOrNull() ?: return@mapNotNull null
            val abbr = team.string("abbreviation") ?: team.string("shortDisplayName") ?: team.string("id") ?: return@mapNotNull null
            SportsTeam(
                id = "${feed.id}:$abbr",
                abbr = abbr.take(5).uppercase(),
                name = team.string("displayName") ?: team.string("name") ?: abbr,
                league = feed.name,
                color = parseColor(team.string("color"), feed.hue),
                logoUrl = team.array("logos").firstOrNull()?.asObjectOrNull()?.string("href"),
            )
        }
    }

    private fun JsonObject.toMatch(feed: SportsFeed): SportsMatch? {
        val eventId = string("id") ?: return null
        val competition = array("competitions").firstOrNull()?.asObjectOrNull() ?: return null
        val competitors = competition.array("competitors").mapNotNull { it.asObjectOrNull() }
        val home = competitors.firstOrNull { it.string("homeAway") == "home" } ?: competitors.firstOrNull()
        val away = competitors.firstOrNull { it.string("homeAway") == "away" } ?: competitors.drop(1).firstOrNull()
        val statusObj = obj("status")
        val statusType = statusObj?.obj("type")
        val status = when (statusType?.string("state")) {
            "in" -> "live"
            "post" -> "final"
            else -> "upcoming"
        }
        return SportsMatch(
            id = "espn-${feed.id}-$eventId",
            providerGameId = eventId,
            sport = feed.id,
            league = feed.name,
            status = status,
            startLabel = string("date")?.toStartLabel().orEmpty(),
            clock = statusType?.string("shortDetail") ?: statusType?.string("detail") ?: statusObj?.string("displayClock").orEmpty(),
            home = home.toSportsTeam(feed, "HOME", 0xFF2F81F7L),
            away = away.toSportsTeam(feed, "AWAY", 0xFFEF4B57L),
            homeScore = home?.string("score")?.toIntOrNull() ?: 0,
            awayScore = away?.string("score")?.toIntOrNull() ?: 0,
        )
    }

    private fun JsonObject?.toSportsTeam(feed: SportsFeed, fallback: String, color: Long): SportsTeam {
        val team = this?.obj("team")
        val abbr = team?.string("abbreviation") ?: fallback
        return SportsTeam(
            id = team?.string("id") ?: abbr,
            abbr = abbr.take(5).uppercase(),
            name = team?.string("displayName") ?: fallback,
            league = feed.name,
            color = parseColor(team?.string("color"), color),
            logoUrl = team?.string("logo"),
        )
    }

    private suspend fun getJson(url: String): JsonElement = withContext(Dispatchers.IO) {
        val cached = cache[url]
        if (cached != null && System.currentTimeMillis() - cached.first < 5 * 60 * 1000) return@withContext cached.second
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        httpClient.catalogueClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("ESPN returned HTTP ${response.code}")
            val parsed = json.parseToJsonElement(response.body?.string().orEmpty())
            cache[url] = System.currentTimeMillis() to parsed
            parsed
        }
    }

    private fun endpoint(feed: SportsFeed, resource: String, params: Map<String, String> = emptyMap()): String {
        val path = if (feed.sport == "soccer") {
            "https://site.api.espn.com/apis/site/v2/sports/soccer/${feed.league}/$resource"
        } else {
            "https://site.api.espn.com/apis/site/v2/sports/${feed.sport}/${feed.league}/$resource"
        }
        val builder = path.toHttpUrl().newBuilder()
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }

    private fun String.toStartLabel(): String = runCatching {
        val instant = Instant.parse(this)
        val local = instant.atZone(ZoneId.systemDefault())
        "${local.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))} ${local.toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a"))}"
    }.getOrDefault("")

    private fun parseColor(value: String?, fallback: Long): Long =
        runCatching { 0xFF000000L or value.orEmpty().removePrefix("#").toLong(16) }.getOrDefault(fallback)

    private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject
    private fun JsonObject.obj(name: String): JsonObject? = get(name) as? JsonObject
    private fun JsonObject.array(name: String): JsonArray = (get(name) as? JsonArray) ?: JsonArray(emptyList())
    private fun JsonObject.string(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull
}
