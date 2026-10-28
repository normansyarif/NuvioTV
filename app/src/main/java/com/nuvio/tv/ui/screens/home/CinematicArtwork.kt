package com.nuvio.tv.ui.screens.home

/** Never treat the model's poster fallback as a horizontal backdrop. */
internal fun cinematicBackdropUrl(background: String?, landscape: String?, poster: String?, originalPoster: String? = null): String? {
    val posterUrls = listOfNotNull(poster?.trim(), originalPoster?.trim())
    return listOfNotNull(background?.trim(), landscape?.trim())
        .firstOrNull { it.isNotEmpty() && it !in posterUrls }
}

internal fun ModernCarouselItem.cinematicBackdropUrl(): String? {
    val meta = metaPreview
    return if (meta != null) {
        cinematicBackdropUrl(meta.background, meta.landscapePoster, meta.poster, meta.rawPosterUrl)
    } else cinematicBackdropUrl(heroPreview.backdrop, heroPreview.frozenBackdropUrl, heroPreview.poster)
}

internal fun cinematicDescription(description: String?, title: String): String =
    description?.trim()?.takeIf { it.isNotEmpty() } ?: title
