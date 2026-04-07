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
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RemoteTitleRating"

@Singleton
class RemoteTitleRatingRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun fetchRating(
        tmdbId: String,
        mediaType: String
    ): Result<Int?> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedMediaType = normalizeMediaType(mediaType)
            val url = AddonBackendConfig.ratingStatusUrl.toHttpUrl().newBuilder()
                .addQueryParameter("tmdb", tmdbId)
                .addQueryParameter("media_type", normalizedMediaType)
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
                parseRatingResponse(bodyString, fallbackMessage = "Failed to load rating")
            }
        }.onFailure { error ->
            Log.w(
                TAG,
                "Failed to fetch rating for tmdb=$tmdbId mediaType=$mediaType: ${error.message}"
            )
        }
    }

    suspend fun setRating(
        tmdbId: String,
        mediaType: String,
        rating: Int
    ): Result<Int?> = withContext(Dispatchers.IO) {
        if (rating !in 1..10) {
            return@withContext Result.failure(IllegalArgumentException("Rating must be between 1 and 10"))
        }

        runCatching {
            val normalizedMediaType = normalizeMediaType(mediaType)
            val payload = JSONObject()
                .put("tmdb", tmdbId)
                .put("rating", rating)
                .put("media_type", normalizedMediaType)
                .toString()

            val request = Request.Builder()
                .url(AddonBackendConfig.ratingUpdateUrl)
                .header("Accept", "*/*")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Failed to update rating (${response.code})")
                }
                parseRatingResponse(bodyString, fallbackMessage = "Failed to update rating")
            }
        }.onFailure { error ->
            Log.w(
                TAG,
                "Failed to set rating for tmdb=$tmdbId mediaType=$mediaType rating=$rating: ${error.message}"
            )
        }
    }

    suspend fun removeRating(
        tmdbId: String,
        mediaType: String
    ): Result<Int?> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedMediaType = normalizeMediaType(mediaType)
            val payload = JSONObject()
                .put("tmdb", tmdbId)
                .put("media_type", normalizedMediaType)
                .put("remove", true)
                .toString()

            val request = Request.Builder()
                .url(AddonBackendConfig.ratingUpdateUrl)
                .header("Accept", "*/*")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Failed to remove rating (${response.code})")
                }
                parseRatingResponse(bodyString, fallbackMessage = "Failed to remove rating")
            }
        }.onFailure { error ->
            Log.w(
                TAG,
                "Failed to remove rating for tmdb=$tmdbId mediaType=$mediaType: ${error.message}"
            )
        }
    }

    private fun parseRatingResponse(
        bodyString: String,
        fallbackMessage: String
    ): Int? {
        if (bodyString.isBlank()) {
            throw IOException(fallbackMessage)
        }

        val body = JSONObject(bodyString)
        if (!body.optBoolean("success", false)) {
            throw IOException(
                body.optString("message").takeIf { it.isNotBlank() }
                    ?: body.optString("error").takeIf { it.isNotBlank() }
                    ?: fallbackMessage
            )
        }

        return body.parseNullableRating()
            ?: body.optJSONObject("trakt")?.parseNullableRating()
    }

    private fun JSONObject.parseNullableRating(): Int? {
        val raw = opt("rating")
        return when (raw) {
            null,
            JSONObject.NULL -> null
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }

    private fun normalizeMediaType(mediaType: String): String {
        return when (mediaType.trim().lowercase()) {
            "movie", "film" -> "movie"
            "series", "tv", "show", "tvshow" -> "tv"
            else -> mediaType.trim().lowercase()
        }
    }
}
