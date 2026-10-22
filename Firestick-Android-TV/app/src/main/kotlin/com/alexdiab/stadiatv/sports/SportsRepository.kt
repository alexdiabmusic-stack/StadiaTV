package com.alexdiab.stadiatv.sports

import com.alexdiab.stadiatv.domain.Athlete
import com.alexdiab.stadiatv.domain.AthleteOverview
import com.alexdiab.stadiatv.domain.GameCentre
import com.alexdiab.stadiatv.domain.Injury
import com.alexdiab.stadiatv.domain.LeaderBoard
import com.alexdiab.stadiatv.domain.League
import com.alexdiab.stadiatv.domain.Match
import com.alexdiab.stadiatv.domain.Racer
import com.alexdiab.stadiatv.domain.RosterGroup
import com.alexdiab.stadiatv.domain.SportsCapability
import com.alexdiab.stadiatv.domain.StandingGroup
import com.alexdiab.stadiatv.domain.Team
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Routes each request to the league's native provider and falls back to ESPN when the native source
 * fails or is blocked on this network. A circuit breaker stops retrying a dead native source for a
 * few minutes, so one blocked host never makes every screen wait on a timeout.
 */
class SportsRepository(
    private val native: Map<String, SportsProvider>,
    private val espn: EspnApi,
) {
    private val espnProviders = ConcurrentHashMap<String, EspnProvider>()
    private val health = ConcurrentHashMap<String, Health>()

    private class Health {
        @Volatile var failures = 0
        @Volatile var openUntil = 0L
    }

    fun espnProvider(league: League): SportsProvider = espnProviders.getOrPut(league.path) { EspnProvider(league, espn) }

    fun hasNativeProvider(league: League) = native.containsKey(league.path)

    /** True when the best source for this league supports the capability. */
    fun supports(league: League, capability: SportsCapability): Boolean =
        native[league.path]?.capabilities?.contains(capability) == true ||
            espnProvider(league).capabilities.contains(capability)

    private suspend fun <T> route(
        league: League,
        capability: SportsCapability,
        call: suspend (SportsProvider) -> T,
    ): T {
        val primary = native[league.path]
        if (primary != null && capability in primary.capabilities && !isOpen(league)) {
            try {
                return call(primary).also { recordSuccess(league) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: UnsupportedCapabilityException) {
                // Not a failure: this source simply doesn't serve it.
            } catch (_: Throwable) {
                recordFailure(league)
            }
        }
        return call(espnProvider(league))
    }

    suspend fun scoreboard(league: League, date: Long? = null): List<Match> =
        route(league, SportsCapability.Scores) { it.scores(date) }

    suspend fun schedule(league: League, start: Long, days: Int): List<Match> =
        route(league, SportsCapability.Schedule) { it.schedule(start, days) }

    suspend fun teams(league: League): List<Team> = route(league, SportsCapability.Teams) { it.teams() }

    suspend fun standings(league: League): List<StandingGroup> = route(league, SportsCapability.Standings) { it.standings() }

    suspend fun roster(league: League, teamId: String): List<RosterGroup> =
        route(league, SportsCapability.Rosters) { it.roster(teamId) }

    suspend fun athlete(league: League, athlete: Athlete): AthleteOverview =
        route(league, SportsCapability.Players) { it.athlete(athlete) }

    suspend fun leaders(league: League): List<LeaderBoard> = route(league, SportsCapability.Leaders) { it.leaders() }

    suspend fun injuries(league: League): List<Injury> = route(league, SportsCapability.Injuries) { it.injuries() }

    suspend fun field(league: League): List<Racer> = route(league, SportsCapability.Field) { it.field() }

    suspend fun gameCentre(match: Match): GameCentre = route(match.league, SportsCapability.GameDetails) { it.gameCentre(match) }

    /** Live detail from the native source for a single match; falls back to the match unchanged. */
    suspend fun enrich(match: Match): Match {
        val provider = native[match.league.path] ?: return match
        if (isOpen(match.league)) return match
        return try {
            provider.enrich(match)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            match
        }
    }

    /**
     * Today's scoreboard for many leagues, emitted league by league as each finishes so screens fill
     * in progressively instead of waiting on the slowest provider.
     */
    fun liveScores(leagues: List<League>, concurrency: Int = 6): Flow<LeagueResult> = channelFlow {
        val gate = Semaphore(concurrency)
        leagues.forEach { league ->
            launch {
                gate.withPermit {
                    val result = runCatching { scoreboard(league) }
                    if (result.exceptionOrNull() is CancellationException) throw result.exceptionOrNull()!!
                    send(LeagueResult(league, result.getOrDefault(emptyList()), result.exceptionOrNull()))
                }
            }
        }
        awaitClose { }
    }

    data class LeagueResult(val league: League, val matches: List<Match>, val error: Throwable?)

    // region Circuit breaker

    private fun isOpen(league: League): Boolean {
        val state = health[league.path] ?: return false
        return state.openUntil > System.currentTimeMillis()
    }

    private fun recordSuccess(league: League) {
        health[league.path]?.let { it.failures = 0; it.openUntil = 0 }
    }

    private fun recordFailure(league: League) {
        val state = health.getOrPut(league.path) { Health() }
        state.failures++
        if (state.failures >= FAILURE_THRESHOLD) {
            state.openUntil = System.currentTimeMillis() + OPEN_MILLIS
            state.failures = 0
        }
    }

    // endregion

    private companion object {
        const val FAILURE_THRESHOLD = 3
        const val OPEN_MILLIS = 5 * 60_000L
    }
}
