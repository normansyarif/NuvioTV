package com.nuvio.tv.data.repository

import android.util.Log
import com.nuvio.tv.core.config.AddonBackendConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RemoteEpisodeWatched"

@Singleton
class RemoteEpisodeWatchedRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun fetchWatchedEpisodes(tmdbId: String): Set<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        val url = AddonBackendConfig.watchedStatusUrl.toHttpUrl().newBuilder()
            .addQueryParameter("tmdb", tmdbId)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Failed to load watched status (${response.code})")
            }
            if (bodyString.isBlank()) return@withContext emptySet()

            return@withContext parseWatchedEpisodes(bodyString)
        }
    }

    suspend fun updateEpisodeWatched(
        tmdbId: String,
        season: Int,
        episode: Int,
        watched: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("tmdb", tmdbId)
                .put("season", season.toString())
                .put("episode", episode)
                .put("watched", if (watched) 1 else 0)
                .toString()

            val referrer = AddonBackendConfig.markReferrerUrl.toHttpUrl().newBuilder()
                .addQueryParameter("tmdb", tmdbId)
                .addQueryParameter("season", season.toString())
                .addQueryParameter("episode", episode.toString())
                .addQueryParameter("page", "1")
                .addQueryParameter("started", "1")
                .build()

            val request = Request.Builder()
                .url(AddonBackendConfig.tmdbMarkUrl)
                .header("Accept", "*/*")
                .header("Content-Type", "application/json")
                .header("Referer", referrer.toString())
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (response.code != 200) {
                    throw IOException("Failed to update watched status (${response.code})")
                }
                if (bodyString.isBlank()) return@use

                val body = JSONObject(bodyString)
                if (!body.optBoolean("success", false)) {
                    val message = body.optString("message")
                        .takeIf { it.isNotBlank() }
                        ?: body.optString("error").takeIf { it.isNotBlank() }
                        ?: "Failed to update watched status"
                    throw IOException(message)
                }
            }
        }.onFailure { error ->
            Log.w(
                TAG,
                "Failed to update watched status for tmdb=$tmdbId season=$season episode=$episode watched=$watched: ${error.message}"
            )
        }
    }

    private fun parseWatchedEpisodes(bodyString: String): Set<Pair<Int, Int>> {
        val watchedSet = linkedSetOf<Pair<Int, Int>>()
        val array = JSONArray(bodyString)
        for (index in 0 until array.length()) {
            val seasonObject = array.optJSONObject(index) ?: continue
            val seasonNumber = seasonObject.optInt("season_number", -1)
            if (seasonNumber < 0) continue

            val watchedEpisodes = seasonObject.optJSONArray("watched_episodes") ?: continue
            for (episodeIndex in 0 until watchedEpisodes.length()) {
                val episodeNumber = watchedEpisodes.optInt(episodeIndex, -1)
                if (episodeNumber > 0) {
                    watchedSet += seasonNumber to episodeNumber
                }
            }
        }
        return watchedSet
    }
}
