package com.nuvio.tv.ui.screens.home

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.domain.model.CatalogRow
import kotlinx.coroutines.launch

internal fun HomeViewModel.scheduleUpNextWatchNextSyncPipeline(rows: List<CatalogRow>) {
    val upNextSeriesRow = rows.firstOrNull { row ->
        row.catalogName.equals("Up Next", ignoreCase = true) &&
            row.apiType.equals("series", ignoreCase = true)
    } ?: return

    val signature = buildString {
        append(catalogLoadGeneration)
        append('|')
        append(upNextSeriesRow.addonId)
        append('|')
        append(upNextSeriesRow.catalogId)
        append('|')
        append(upNextSeriesRow.addonBaseUrl)
        upNextSeriesRow.items.forEach { item ->
            append('|')
            append(item.id)
            append(':')
            append(item.name)
            append(':')
            append(item.poster.orEmpty())
            append(':')
            append(item.backdropUrl.orEmpty())
            append(':')
            append(item.description.orEmpty())
        }
    }

    if (signature == lastRequestedUpNextWatchNextSignature) {
        return
    }

    lastRequestedUpNextWatchNextSignature = signature
    upNextWatchNextSyncJob?.cancel()
    upNextWatchNextSyncJob = viewModelScope.launch {
        runCatching {
            upNextWatchNextSyncer.syncSeriesCatalog(upNextSeriesRow)
        }.onSuccess {
            lastAppliedUpNextWatchNextSignature = signature
        }.onFailure { error ->
            if (lastRequestedUpNextWatchNextSignature == signature) {
                lastRequestedUpNextWatchNextSignature = lastAppliedUpNextWatchNextSignature
            }
            Log.w(
                HomeViewModel.TAG,
                "Failed to sync Up Next watch next row: ${error.message}"
            )
        }
    }
}
