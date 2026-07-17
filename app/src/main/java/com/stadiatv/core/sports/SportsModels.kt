package com.stadiatv.core.sports

data class SportsFeed(
    val id: String,
    val sport: String,
    val league: String,
    val name: String,
    val mark: String,
    val hue: Long,
    val hasNews: Boolean = true,
    val hasTeams: Boolean = true,
)

data class SportsTeam(
    val id: String,
    val abbr: String,
    val name: String,
    val league: String,
    val color: Long,
    val logoUrl: String?,
)

data class SportsMatch(
    val id: String,
    val providerGameId: String,
    val sport: String,
    val league: String,
    val status: String,
    val startLabel: String,
    val clock: String,
    val home: SportsTeam,
    val away: SportsTeam,
    val homeScore: Int,
    val awayScore: Int,
)

data class SportsNews(
    val id: String,
    val sport: String,
    val league: String,
    val headline: String,
    val description: String,
    val imageUrl: String?,
    val webUrl: String?,
)

data class SportsHubData(
    val matches: List<SportsMatch>,
    val teams: List<SportsTeam>,
    val news: List<SportsNews>,
    val errors: List<String>,
)

object StadiaSportsFeeds {
    val all = listOf(
        SportsFeed("college-football", "football", "college-football", "College Football", "CFB", 0xFF9F7AEAL),
        SportsFeed("nfl", "football", "nfl", "NFL", "NFL", 0xFF8B5CF6L),
        SportsFeed("mlb", "baseball", "mlb", "MLB", "MLB", 0xFFE05A6AL),
        SportsFeed("college-baseball", "baseball", "college-baseball", "College Baseball", "CBB", 0xFFF97316L, hasNews = false, hasTeams = false),
        SportsFeed("nhl", "hockey", "nhl", "NHL", "NHL", 0xFF38BDF8L),
        SportsFeed("nba", "basketball", "nba", "NBA", "NBA", 0xFFF5883DL),
        SportsFeed("wnba", "basketball", "wnba", "WNBA", "WNBA", 0xFF22C197L),
        SportsFeed("womens-college-basketball", "basketball", "womens-college-basketball", "Women's College Basketball", "WBB", 0xFFEC4899L),
        SportsFeed("mens-college-basketball", "basketball", "mens-college-basketball", "Men's College Basketball", "MBB", 0xFF3B82F6L),
        SportsFeed("soccer-eng.1", "soccer", "eng.1", "Premier League", "EPL", 0xFF34D17AL),
        SportsFeed("soccer-usa.1", "soccer", "usa.1", "MLS", "MLS", 0xFF14B8A6L),
    )

    fun byId(id: String): SportsFeed = all.firstOrNull { it.id == id } ?: all.first()
}
