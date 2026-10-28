package com.nuvio.tv.ui.screens.detail

internal fun cinematicPlayLabel(isSeries: Boolean, season: Int?, episode: Int?): String = when {
    !isSeries -> "Play"
    episode == null -> "Play Episode 1"
    season == null || season <= 1 -> "Play Episode $episode"
    else -> "Play S$season E$episode"
}
