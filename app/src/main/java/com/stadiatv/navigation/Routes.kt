package com.stadiatv.navigation

object Routes {
    const val Onboarding = "onboarding"
    const val Sources = "sources"
    const val Home = "home"
    const val Live = "live"
    const val Sports = "sports"
    const val Guide = "guide"
    const val Search = "search"
    const val Favorites = "favorites"
    const val Recent = "recent"
    const val Movies = "movies"
    const val Series = "series"
    const val Settings = "settings"
    const val EpgMapping = "epg-mapping"
    const val Player = "player/{mediaId}"
    fun player(mediaId: String) = "player/$mediaId"
}
