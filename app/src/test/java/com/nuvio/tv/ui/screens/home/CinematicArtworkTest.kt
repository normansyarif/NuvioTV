package com.nuvio.tv.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class CinematicArtworkTest {
    @Test fun posterOnlyMetadataDoesNotExpand() {
        assertNull(cinematicBackdropUrl(null, null, "poster.jpg"))
        assertNull(cinematicBackdropUrl("  ", "", "poster.jpg"))
    }
    @Test fun posterFallbackIsNotAHorizonalImage() {
        assertNull(cinematicBackdropUrl("poster.jpg", null, "poster.jpg"))
        assertNull(cinematicBackdropUrl("original.jpg", null, "custom-poster.jpg", "original.jpg"))
    }
    @Test fun explicitHorizontalArtworkCanExpand() {
        assertEquals("backdrop.jpg", cinematicBackdropUrl("backdrop.jpg", null, "poster.jpg"))
        assertEquals("landscape.jpg", cinematicBackdropUrl(null, "landscape.jpg", "poster.jpg"))
    }
    @Test fun missingDescriptionsShowTheTitle() {
        assertEquals("Example title", cinematicDescription(null, "Example title"))
        assertEquals("Example title", cinematicDescription("  ", "Example title"))
        assertEquals("A description", cinematicDescription(" A description ", "Example title"))
    }
}
