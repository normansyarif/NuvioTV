package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/** A fresh draw when the app enters the foreground, shared across its home tabs. */
internal object HeroSession {
    private val mutableSeed = MutableStateFlow(Random.nextLong())
    val seed = mutableSeed.asStateFlow()

    fun begin() {
        mutableSeed.value = Random.nextLong()
    }
}

/** Use catalog rank to define the pool; session randomness only chooses within it. */
internal fun selectSessionHeroes(rows: List<CatalogRow>, seed: Long): List<MetaPreview> =
    rows.flatMap { it.items.take(10) }
        .filter {
            it.apiType in setOf("movie", "series") &&
                cinematicBackdropUrl(it.background, it.landscapePoster, it.poster, it.rawPosterUrl) != null
        }
        .distinctBy { "${it.apiType}:${it.id}" }
        // An identity-based draw stays stable as catalogs finish loading or paginate.
        .sortedWith(compareBy<MetaPreview> {
            Random(seed xor "${it.apiType}:${it.id}".hashCode().toLong()).nextLong()
        }.thenBy { "${it.apiType}:${it.id}" })
        .distinctBy { it.apiType }
