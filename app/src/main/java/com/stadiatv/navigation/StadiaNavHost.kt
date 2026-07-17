package com.stadiatv.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.stadiatv.feature.favorites.FavoritesScreen
import com.stadiatv.feature.guide.GuideScreen
import com.stadiatv.feature.home.HomeScreen
import com.stadiatv.feature.live.LiveScreen
import com.stadiatv.feature.movies.MoviesScreen
import com.stadiatv.feature.onboarding.OnboardingScreen
import com.stadiatv.feature.onboarding.OnboardingViewModel
import com.stadiatv.feature.player.PlayerScreen
import com.stadiatv.feature.recent.RecentScreen
import com.stadiatv.feature.search.SearchScreen
import com.stadiatv.feature.series.SeriesScreen
import com.stadiatv.feature.settings.EpgMappingScreen
import com.stadiatv.feature.settings.SettingsScreen
import com.stadiatv.feature.sources.SourcesScreen
import com.stadiatv.feature.sports.SportsScreen

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StadiaNavHost() {
    val nav = rememberNavController()
    MaterialTheme {
        Surface {
            NavHost(navController = nav, startDestination = Routes.Onboarding) {
                composable(Routes.Onboarding) {
                    val vm: OnboardingViewModel = hiltViewModel()
                    OnboardingScreen(vm, onDone = { nav.navigate(Routes.Home) { popUpTo(Routes.Onboarding) { inclusive = true } } })
                }
                composable(Routes.Sources) { SourcesScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.Home) {
                    HomeScreen(
                        onOpenLive = { nav.navigate(Routes.Live) },
                        onOpenSports = { nav.navigate(Routes.Sports) },
                        onOpenSources = { nav.navigate(Routes.Sources) },
                    )
                }
                composable(Routes.Live) { LiveScreen(onPlay = { nav.navigate(Routes.player(it)) }, onBack = { nav.popBackStack() }) }
                composable(Routes.Sports) { SportsScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.Guide) { GuideScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.Search) { SearchScreen(onPlay = { nav.navigate(Routes.player(it)) }, onBack = { nav.popBackStack() }) }
                composable(Routes.Favorites) { FavoritesScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.Recent) { RecentScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.Movies) { MoviesScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.Series) { SeriesScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.Settings) { SettingsScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.EpgMapping) { EpgMappingScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.Player) { backStack ->
                    PlayerScreen(mediaId = backStack.arguments?.getString("mediaId").orEmpty(), onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
