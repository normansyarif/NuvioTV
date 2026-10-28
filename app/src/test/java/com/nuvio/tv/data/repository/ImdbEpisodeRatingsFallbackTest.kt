package com.nuvio.tv.data.repository

import com.nuvio.tv.data.remote.api.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class ImdbEpisodeRatingsFallbackTest {
    @Test fun unavailablePrimaryAndMissingTmdbKeyUsePublicIdMapping() = runBlocking {
        val primary = mockk<ImdbTapframeApi>()
        val fallback = mockk<SeriesGraphApi>()
        coEvery { primary.getSeasonRatings("tt1266020") } returns Response.error(503, "unavailable".toResponseBody())
        coEvery { primary.getRatingIds("tt1266020") } returns Response.success(EpisodeRatingIdResponse(EpisodeRatingIds(8592)))
        coEvery { fallback.getSeasonRatings(8592) } returns Response.success(listOf(
            SeriesGraphSeasonRatingsDto(listOf(SeriesGraphEpisodeRatingDto(seasonNumber = 2, episodeNumber = 1, voteAverage = 8.0)))
        ))
        assertEquals(mapOf((2 to 1) to 8.0), ImdbEpisodeRatingsRepository(primary, fallback).getEpisodeRatings("tt1266020", null))
        coVerify(exactly = 1) { fallback.getSeasonRatings(8592) }
    }
}
