package com.nuvio.tv.ui.navigation

import android.content.Intent
import android.net.Uri
import android.util.Log

object NuvioDeepLink {
    private const val TAG = "NuvioDeepLink"

    fun routeFromIntent(intent: Intent?): String? = routeFromUri(intent?.data)

    fun routeFromUri(uri: Uri?): String? {
        if (uri == null) return null
        if (!uri.scheme.equals("nuvio", ignoreCase = true)) return null

        return when (uri.host?.lowercase()) {
            "detail" -> detailRoute(uri)
            else -> null
        }
    }

    private fun detailRoute(uri: Uri): String? {
        val itemId = uri.pathSegments.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val itemType = uri.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val addonBaseUrl = uri.getQueryParameter("addonBaseUrl")
        val returnFocusSeason = uri.queryInt("season") ?: uri.queryInt("returnFocusSeason")
        val returnFocusEpisode = uri.queryInt("episode") ?: uri.queryInt("returnFocusEpisode")
        val returnToHomeOnBack = uri.queryBoolean("returnToHomeOnBack") ?: false

        val route = Screen.Detail.createRoute(
            itemId = itemId,
            itemType = itemType,
            addonBaseUrl = addonBaseUrl,
            returnFocusSeason = returnFocusSeason,
            returnFocusEpisode = returnFocusEpisode,
            returnToHomeOnBack = returnToHomeOnBack
        )
        Log.d(TAG, "Resolved uri=$uri to route=$route")
        return route
    }

    private fun Uri.queryInt(name: String): Int? = getQueryParameter(name)?.toIntOrNull()

    private fun Uri.queryBoolean(name: String): Boolean? =
        getQueryParameter(name)?.toBooleanStrictOrNull()
}
