package com.nuvio.tv.ui.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class CinematicDetailActionsTest {
    @Test fun moviesAlwaysExposePlay() {
        assertEquals("Play", cinematicPlayLabel(false, null, null))
    }
    @Test fun seriesStartAtTheFirstEpisodeUntilProgressResolves() {
        assertEquals("Play Episode 1", cinematicPlayLabel(true, null, null))
    }
    @Test fun seriesLabelIdentifiesTheResolvedNextEpisode() {
        assertEquals("Play Episode 5", cinematicPlayLabel(true, 1, 5))
        assertEquals("Play S3 E12", cinematicPlayLabel(true, 3, 12))
    }
}
