package com.nuvio.tv.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class CinematicCatalogLoadingTest {
    @Test fun pendingCatalogUsesQueueKeyInsteadOfDisplayIdentity() {
        val row = HeroCarouselRow(key = "addon_urlhash_series_top10", title = "Top 10", globalRowIndex = 0,
            items = com.nuvio.tv.ui.util.StableList(), addonId = "addon", apiType = "series", catalogId = "top10", isLoading = true)
        assertEquals("addon_series_top10", row.cinematicLazyLoadKey())
        assertNull(row.copy(isLoading = false).cinematicLazyLoadKey())
    }
    @Test fun nonCatalogRowsDoNotStartCatalogRequests() {
        val row = HeroCarouselRow(key = "continue_watching", title = "Continue Watching", globalRowIndex = 0,
            items = com.nuvio.tv.ui.util.StableList(), isLoading = true)
        assertNull(row.cinematicLazyLoadKey())
    }
}
