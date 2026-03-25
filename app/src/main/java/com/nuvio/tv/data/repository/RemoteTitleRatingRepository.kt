package com.nuvio.tv.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RemoteTitleRating"
private const val TITLE_RATING_GET_URL = "https://addon.syf.my.id/stremio-watched/trakt-rating.php"
private const val TITLE_RATING_POST_URL = "https://addon.syf.my.id/stremio-watched/my-rate.php"

data class RemoteTitleRatingResult(
    val rating: Int?
)

@Singleton
class RemoteTitleRatingRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun fetchRating(tmdbId: String, mediaType: String): Result<RemoteTitleRatingResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = TITLE_RATING_GET_URL.toHttpUrl().newBuilder()
                    .addQueryParameter("tmdb", tmdbId)
                    .addQueryParameter("media_type", mediaType)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException("Failed to load rating (${response.code})")
                    }
                    parseRatingResponse(bodyString)
                }
            }.onFailure { error ->
                Log.w(TAG, "Failed to fetch rating for tmdb=$tmdbId mediaType=$mediaType: ${error.message}")
            }
        }

    suspend fun setRating(tmdbId: String, mediaType: String, rating: Int): Result<RemoteTitleRatingResult> =
        postRating(
            tmdbId = tmdbId,
            mediaType = mediaType,
            payload = JSONObject()
                .put("tmdb", tmdbId)
                .put("rating", rating)
                .put("media_type", mediaType)
                .toString()
        )

    suspend fun removeRating(tmdbId: String, mediaType: String): Result<RemoteTitleRatingResult> =
        postRating(
            tmdbId = tmdbId,
            mediaType = mediaType,
            payload = JSONObject()
                .put("tmdb", tmdbId)
                .put("media_type", mediaType)
                .put("remove", true)
                .toString()
        )

    private suspend fun postRating(
        tmdbId: String,
        mediaType: String,
        payload: String
    ): Result<RemoteTitleRatingResult> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(TITLE_RATING_POST_URL)
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Failed to update rating (${response.code})")
                }
                parseRatingResponse(bodyString)
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to update rating for tmdb=$tmdbId mediaType=$mediaType: ${error.message}")
        }
    }

    private fun parseRatingResponse(bodyString: String): RemoteTitleRatingResult {
        if (bodyString.isBlank()) {
            throw IOException("Empty rating response")
        }
        val body = JSONObject(bodyString)
        if (!body.optBoolean("success", false)) {
            val message = body.optString("message")
                .takeIf { it.isNotBlank() }
                ?: body.optString("error").takeIf { it.isNotBlank() }
                ?: "Rating request failed"
            throw IOException(message)
        }

        val ratingValue = if (body.isNull("rating")) null else body.optInt("rating")
        return RemoteTitleRatingResult(rating = ratingValue)
    }
}
