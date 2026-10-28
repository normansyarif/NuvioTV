package com.nuvio.tv.ui.screens.detail

internal fun sortedSeasonIndex(seasons: List<Int>, season: Int): Int =
    (seasons.filter { it > 0 }.sorted() + seasons.filter { it == 0 }).indexOf(season).coerceAtLeast(0)

internal fun adjacentEpisodeSeason(sortedSeasons: List<Int>, season: Int, direction: Int): Int? {
    require(direction == -1 || direction == 1)
    val current = sortedSeasons.indexOf(season)
    if (current < 0) return null
    return sortedSeasons.getOrNull(current + direction)
}
