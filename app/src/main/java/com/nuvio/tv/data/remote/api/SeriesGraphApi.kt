package com.nuvio.tv.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class SeriesGraphEpisodeRatingDto(
    @field:Json(name = "season_number") val seasonNumber: Int? = null,
    @field:Json(name = "episode_number") val episodeNumber: Int? = null,
    @field:Json(name = "vote_average") val voteAverage: Double? = null,
    @field:Json(name = "name") val name: String? = null,
    @field:Json(name = "tconst") val tconst: String? = null
)

@JsonClass(generateAdapter = true)
data class SeriesGraphSeasonRatingsDto(
    @field:Json(name = "episodes") val episodes: List<SeriesGraphEpisodeRatingDto>? = null
)

interface SeriesGraphApi {
    @GET("api/shows/{tmdbId}/season-ratings")
    suspend fun getSeasonRatings(
        @Path("tmdbId") tmdbId: Int
    ): Response<List<SeriesGraphSeasonRatingsDto>>
}

@JsonClass(generateAdapter = true)
data class EpisodeRatingIdResponse(@field:Json(name = "meta") val meta: EpisodeRatingIds? = null)

@JsonClass(generateAdapter = true)
data class EpisodeRatingIds(@field:Json(name = "moviedb_id") val tmdbId: Int? = null)

interface ImdbTapframeApi {
    @GET("https://v3-cinemeta.strem.io/meta/series/{imdbId}.json")
    suspend fun getRatingIds(@Path("imdbId") imdbId: String): Response<EpisodeRatingIdResponse>

    @GET("api/shows/{imdbId}/season-ratings")
    suspend fun getSeasonRatings(
        @Path("imdbId") imdbId: String
    ): Response<List<SeriesGraphSeasonRatingsDto>>
}
