package com.nuvio.tv.ui.screens.detail

import org.junit.Assert.*
import org.junit.Test

class EpisodeSeasonNavigationTest {
    @Test fun upAndDownStayInTheSeasonList() {
        val seasons = listOf(1, 2, 3, 4, 0)
        assertEquals(3, adjacentEpisodeSeason(seasons, 2, 1))
        assertEquals(2, adjacentEpisodeSeason(seasons, 3, -1))
        assertEquals(0, adjacentEpisodeSeason(seasons, 4, 1))
        assertNull(adjacentEpisodeSeason(seasons, 1, -1))
        assertNull(adjacentEpisodeSeason(seasons, 0, 1))
    }
    @Test fun lazySeasonScrollingUsesTheDisplayedOrder() {
        assertEquals(1, sortedSeasonIndex(listOf(0, 3, 1, 2), 2))
        assertEquals(3, sortedSeasonIndex(listOf(0, 3, 1, 2), 0))
    }
}
