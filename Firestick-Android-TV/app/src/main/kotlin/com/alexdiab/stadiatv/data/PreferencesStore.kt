package com.alexdiab.stadiatv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import com.alexdiab.stadiatv.domain.League
import com.alexdiab.stadiatv.domain.Match
import com.alexdiab.stadiatv.domain.SportGroup
import com.alexdiab.stadiatv.domain.Team
import com.alexdiab.stadiatv.domain.TeamSide
import com.alexdiab.stadiatv.net.AppJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** A followed team. Persisted with its name and crest so Following renders without a network round trip. */
@Immutable
@Serializable
data class FavoriteTeam(
    val leaguePath: String,
    val teamId: String,
    val name: String,
    val abbreviation: String = "",
    val logoUrl: String? = null,
) {
    val key: String get() = "$leaguePath-$teamId"
    val league: League? get() = League.find(leaguePath)

    fun matches(league: League, side: TeamSide): Boolean {
        if (league.path != leaguePath) return false
        if (!side.teamId.isNullOrEmpty() && side.teamId == teamId) return true
        if (abbreviation.isNotEmpty() && side.abbreviation.equals(abbreviation, ignoreCase = true)) return true
        return name.isNotEmpty() && side.displayName.equals(name, ignoreCase = true)
    }

    companion object {
        fun from(team: Team) = FavoriteTeam(team.league.path, team.id, team.name, team.abbreviation, team.logoUrl)
    }
}

/** A multiview arrangement saved to one of the four slots: which channels, in which layout, and which has sound. */
@Immutable
@Serializable
data class SavedMultiview(val channelKeys: List<String>, val layout: String, val audioKey: String? = null)

enum class Appearance(val label: String) { System("System"), Dark("Dark"), Light("Light") }

@Immutable
@Serializable
data class UserSettings(
    val hasCompletedOnboarding: Boolean = false,
    val selectedLeagueIds: Set<String> = emptySet(),
    val favoriteTeams: List<FavoriteTeam> = emptyList(),
    val favoriteChannelIds: Set<String> = emptySet(),
    val preferredStreamLanguages: Set<String> = setOf("en"),
    val spoilerFreeMode: Boolean = false,
    /** Delay applied to score updates so they don't spoil a slightly delayed stream. */
    val sportsScoreDelaySeconds: Int = 0,
    val showLiveScoreBadge: Boolean = true,
    val showChannelNumbers: Boolean = false,
    val playerPanelTimeoutSeconds: Int = 5,
    val preferUhdStreams: Boolean = false,
    /** HD before standard definition when a channel has both. Off prefers standard definition (slow connections, data caps). */
    val preferHdStreams: Boolean = true,
    /** "CA" or "US"; empty follows the device's region. */
    val homeCountry: String = "",
    /** The city whose local stations the guide shows by default ("ottawa"); empty guesses from the device's time zone. */
    val homeMarket: String = "",
    /** Countries whose channels are loaded into the guide, in addition to the home country. */
    val guideCountries: Set<String> = setOf("CA", "US"),
    /** Channels (lineup entry keys) the viewer starred, hid, and ordered by hand. */
    val favoriteEntryKeys: Set<String> = emptySet(),
    val hiddenEntryKeys: Set<String> = emptySet(),
    val entryOrder: List<String> = emptyList(),
    val hideKidsChannels: Boolean = false,
    val showFreeStreaming: Boolean = false,
    /** Spanish-language channels are a separate catalogue, hidden until asked for. */
    val showSpanishChannels: Boolean = false,
    /** Subtitles: on or off at the start of a channel, and the language to choose when they are on. */
    val captionsEnabled: Boolean = false,
    val captionLanguage: String = "",
    val guideTimeScaleMinutes: Int = 60,
    val doubleTapSeeks: Boolean = true,
    val playerBufferProfile: String = "Balanced",
    /** Saved multiview layouts keyed by slot "1".."4". */
    val multiviewSlots: Map<String, SavedMultiview> = emptyMap(),
) {
    /** Leagues the user follows, in catalog order. Falls back to the full catalog when nothing is chosen. */
    val followedLeagues: List<League>
        get() = League.ALL.filter { it.path in selectedLeagueIds }.ifEmpty { League.ALL }

    val explicitlyFollowedLeagues: List<League>
        get() = League.ALL.filter { it.path in selectedLeagueIds }

    /** True once the viewer has chosen at least one league; until then every league counts as followed. */
    val followsExplicitly: Boolean get() = selectedLeagueIds.isNotEmpty()

    /** Whether any choice (a league or a team) has been made, which is what Home personalises from. */
    val isPersonalised: Boolean get() = followsExplicitly || favoriteTeams.isNotEmpty()

    /** A game that belongs to the viewer: in a followed league, or played by a starred team in any league. */
    fun inScope(match: Match): Boolean = !followsExplicitly || match.league.path in selectedLeagueIds || involvesFavorite(match)

    fun isFavorite(team: Team): Boolean = favoriteTeams.any { it.leaguePath == team.league.path && it.teamId == team.id }

    fun involvesFavorite(match: Match): Boolean {
        if (favoriteTeams.isEmpty()) return false
        return favoriteTeams.any { it.matches(match.league, match.home) || it.matches(match.league, match.away) }
    }

    /** Stable signature of everything the home feed depends on, to know when to reload. */
    val feedSignature: String
        get() = followedLeagues.joinToString(",") { it.path } + "|" + favoriteTeams.joinToString(",") { it.key }
}

/**
 * Single source of truth for user settings. Everything is one JSON document (like the iOS
 * `UserPreferences`), exposed as a [StateFlow] so Compose recomposes only what changed.
 * Keys written by the original Android build are migrated on first launch.
 */
class PreferencesStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    val current: UserSettings get() = _settings.value

    fun update(transform: (UserSettings) -> UserSettings) {
        _settings.update(transform)
        persist()
    }

    fun completeOnboarding() = update { it.copy(hasCompletedOnboarding = true) }
    fun resetOnboarding() = update { it.copy(hasCompletedOnboarding = false) }

    fun toggleLeague(league: League) = update {
        it.copy(selectedLeagueIds = if (league.path in it.selectedLeagueIds) it.selectedLeagueIds - league.path else it.selectedLeagueIds + league.path)
    }

    /** [pruneTeams] also drops starred teams from leagues that are no longer chosen, as setup does when it narrows the leagues. */
    fun setLeagues(paths: Set<String>, pruneTeams: Boolean = false) = update {
        it.copy(selectedLeagueIds = paths, favoriteTeams = if (pruneTeams) Favourites.pruneTeams(it.favoriteTeams, paths) else it.favoriteTeams)
    }

    /** A sport is chosen as a whole: all of its leagues on, or all off. */
    fun toggleSport(group: SportGroup) = update { it.copy(selectedLeagueIds = Favourites.toggleSport(it.selectedLeagueIds, group)) }

    fun toggleFavorite(team: Team) = update { settings ->
        val existing = settings.favoriteTeams.firstOrNull { it.leaguePath == team.league.path && it.teamId == team.id }
        settings.copy(
            favoriteTeams = if (existing != null) settings.favoriteTeams - existing else settings.favoriteTeams + FavoriteTeam.from(team),
        )
    }

    fun addFavorite(team: FavoriteTeam) = update { if (it.favoriteTeams.any { f -> f.key == team.key }) it else it.copy(favoriteTeams = it.favoriteTeams + team) }

    fun removeFavorite(team: FavoriteTeam) = update { settings -> settings.copy(favoriteTeams = settings.favoriteTeams.filterNot { it.key == team.key }) }

    fun toggleFavoriteEntry(key: String) = update {
        it.copy(favoriteEntryKeys = if (key in it.favoriteEntryKeys) it.favoriteEntryKeys - key else it.favoriteEntryKeys + key)
    }

    fun toggleHiddenEntry(key: String) = update {
        it.copy(hiddenEntryKeys = if (key in it.hiddenEntryKeys) it.hiddenEntryKeys - key else it.hiddenEntryKeys + key)
    }

    fun toggleFavoriteChannel(channelId: String) = update {
        it.copy(favoriteChannelIds = if (channelId in it.favoriteChannelIds) it.favoriteChannelIds - channelId else it.favoriteChannelIds + channelId)
    }

    private fun persist() {
        prefs.edit().putString(KEY_SETTINGS, AppJson.encodeToString(_settings.value)).apply()
    }

    private fun load(): UserSettings {
        prefs.getString(KEY_SETTINGS, null)?.let { stored ->
            runCatching { return AppJson.decodeFromString<UserSettings>(stored) }
        }
        return migrateLegacy().also { settings ->
            prefs.edit().putString(KEY_SETTINGS, AppJson.encodeToString(settings)).apply()
        }
    }

    /** Reads the v1 keys the original Android build wrote, so an update never loses someone's setup. */
    private fun migrateLegacy(): UserSettings {
        val legacy = prefs.getBoolean("onboarding", false) || prefs.contains("sports") || prefs.contains("leagues")
        if (!legacy) return UserSettings()
        val leagues = prefs.getStringSet("leagues", emptySet()).orEmpty().filter { League.find(it) != null }.toSet()
        val favorites = prefs.getStringSet("favoriteTeams", emptySet()).orEmpty().mapNotNull { raw ->
            val league = League.ALL.firstOrNull { raw.startsWith(it.path + "-") } ?: return@mapNotNull null
            val teamId = raw.removePrefix(league.path + "-")
            // Display details are backfilled from the team list the next time Following opens.
            FavoriteTeam(league.path, teamId, name = "")
        }
        return UserSettings(
            hasCompletedOnboarding = prefs.getBoolean("onboarding", false),
            selectedLeagueIds = leagues,
            favoriteTeams = favorites,
            favoriteChannelIds = prefs.getStringSet("favoriteChannels", emptySet()).orEmpty().toSet(),
            preferredStreamLanguages = prefs.getStringSet("streamLanguages", null)?.toSet() ?: setOf("en"),
            spoilerFreeMode = prefs.getBoolean("spoilerFree", false),
            showLiveScoreBadge = prefs.getBoolean("liveScoreOverlay", true),
        )
    }

    private companion object {
        const val FILE = "stadiatv.preferences.v1"
        const val KEY_SETTINGS = "settings.v2"
    }
}
