package com.nuvio.tv.core.config

import com.nuvio.tv.BuildConfig

object AddonBackendConfig {
    private const val DEFAULT_BASE_URL = "https://addon.syf.my.id"

    private val normalizedBaseUrl: String
        get() = BuildConfig.ADDON_BACKEND_BASE_URL
            .trim()
            .ifBlank { DEFAULT_BASE_URL }
            .trimEnd('/')

    val ratingStatusUrl: String
        get() = "$normalizedBaseUrl/stremio-watched/trakt-rating.php"

    val ratingUpdateUrl: String
        get() = "$normalizedBaseUrl/stremio-watched/my-rate.php"

    val watchedStatusUrl: String
        get() = "$normalizedBaseUrl/stremio-watched/watched-status.php"

    val tmdbMarkUrl: String
        get() = "$normalizedBaseUrl/stremio/tmdb_mark.php"

    val markReferrerUrl: String
        get() = "$normalizedBaseUrl/stremio-watched/mark.php"

    val allAddonsUrl: String
        get() = "$normalizedBaseUrl/stremio-backend/api/all-addons"
}
