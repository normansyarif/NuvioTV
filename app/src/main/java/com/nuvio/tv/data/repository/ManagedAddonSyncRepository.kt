package com.nuvio.tv.data.repository

import android.util.Log
import com.nuvio.tv.core.config.AddonBackendConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ManagedAddonSync"

@Singleton
class ManagedAddonSyncRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun fetchManagedAddonUrls(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(AddonBackendConfig.allAddonsUrl)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Failed to sync addons (${response.code})")
                }
                parseManagedAddonUrls(bodyString)
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to fetch managed addon list: ${error.message}")
        }
    }

    private fun parseManagedAddonUrls(bodyString: String): List<String> {
        if (bodyString.isBlank()) return emptyList()

        val payload = JSONObject(bodyString)
        val addons = payload.optJSONArray("addons") ?: return emptyList()

        return buildList {
            for (index in 0 until addons.length()) {
                val item = addons.optJSONObject(index) ?: continue
                val transportUrl = item.optString("transportUrl")
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?: continue
                val position = when (val rawPosition = item.opt("position")) {
                    is Number -> rawPosition.toInt()
                    is String -> rawPosition.toIntOrNull() ?: Int.MAX_VALUE
                    else -> Int.MAX_VALUE
                }
                add(position to transportUrl)
            }
        }
            .sortedWith(compareBy<Pair<Int, String>>({ it.first }, { it.second }))
            .distinctBy { (_, url) -> normalizeUrl(url) }
            .map { (_, url) -> url }
    }

    private fun normalizeUrl(url: String): String {
        return url.trim().trimEnd('/').lowercase()
    }
}
