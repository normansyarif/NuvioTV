package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.*
import org.junit.Test

class SessionHeroSelectionTest {
    @Test
    fun `draws only from each catalogs first ten in its supplied order`() {
        val rows = listOf(row("a"), row("b"), row("shows", ContentType.SERIES))
        val eligible = rows.flatMap { it.items.take(10) }.map { it.id }.toSet()
        val draws = (0L..100L).flatMap { selectSessionHeroes(rows, it) }
        assertTrue(draws.all { it.id in eligible })
        assertTrue(draws.any { it.id.startsWith("a-") })
        assertTrue(draws.any { it.id.startsWith("b-") })
        // Reversing a catalog changes which titles qualify, without sorting by ID.
        val reversed = rows.first().let { it.copy(items = it.items.reversed()) }
        assertTrue((0L..100L).flatMap { selectSessionHeroes(listOf(reversed), it) }
            .all { it.id in reversed.items.take(10).map { item -> item.id } })
    }

    @Test
    fun `same session is stable across recomputes catalog arrival order and pagination`() {
        val rows = listOf(row("movies"), row("shows", ContentType.SERIES))
        val expected = selectSessionHeroes(rows, 42L)
        assertEquals(expected, selectSessionHeroes(rows, 42L))
        assertEquals(expected, selectSessionHeroes(rows.reversed(), 42L))
        assertEquals(expected, selectSessionHeroes(rows.map { it.copy(items = it.items.take(10)) }, 42L))
        assertTrue((0L..100L).map { selectSessionHeroes(rows, it).map { item -> item.id } }.distinct().size > 1)
    }

    @Test
    fun `both types get a hero even with many movie sources`() {
        val rows = (1..12).map { row("movies-$it") } + row("shows", ContentType.SERIES)
        assertEquals(setOf("movie", "series"), selectSessionHeroes(rows, 42L).map { it.apiType }.toSet())
    }

    @Test
    fun `missing artwork within top ten never falls through to title eleven`() {
        val row = row("movies").let { source ->
            source.copy(items = source.items.mapIndexed { index, item ->
                if (index < 10) item.copy(background = null) else item
            })
        }
        assertTrue(selectSessionHeroes(listOf(row), 42L).isEmpty())
        assertTrue(selectSessionHeroes(emptyList(), 42L).isEmpty())
    }

    private fun row(id: String, type: ContentType = ContentType.MOVIE) = CatalogRow(
        addonId = id, addonName = id, addonBaseUrl = "https://example.test/$id",
        catalogId = "popular", catalogName = "Popular", type = type,
        items = (1..30).map { rank ->
            MetaPreview(
                id = "$id-$rank", name = "$id $rank", type = type,
                poster = null, posterShape = PosterShape.POSTER,
                background = "https://example.test/$id/$rank.jpg", logo = null,
                description = null, releaseInfo = null, imdbRating = null, genres = emptyList()
            )
        }
    )
}
