package com.nuvio.tv.core.tvhome

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.supportsExtra
import com.nuvio.tv.domain.model.skipStep
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

@Singleton
class UpNextCatalogWatchNextCoordinator @Inject constructor(
    private val addonRepository: AddonRepository,
    private val catalogRepository: CatalogRepository,
    private val upNextWatchNextSyncer: UpNextWatchNextSyncer
) {
    suspend fun refreshAndSyncInstalledUpNextSeriesCatalog() {
        val addons = addonRepository.getInstalledAddons().first()
        var matchedAddonCatalog: Pair<com.nuvio.tv.domain.model.Addon, CatalogDescriptor>? = null
        for (addon in addons) {
            val catalog = addon.catalogs.firstOrNull { isUpNextSeriesCatalog(it) } ?: continue
            matchedAddonCatalog = addon to catalog
            break
        }
        val match = matchedAddonCatalog ?: return

        val (addon, catalog) = match
        var latestSuccess: CatalogRow? = null
        catalogRepository.getCatalog(
            addonBaseUrl = addon.baseUrl,
            addonId = addon.id,
            addonName = addon.displayName,
            catalogId = catalog.id,
            catalogName = catalog.name,
            type = catalog.apiType,
            skip = 0,
            skipStep = catalog.skipStep(),
            supportsSkip = catalog.supportsExtra("skip"),
            forceRefresh = true
        ).collect { result ->
            if (result is NetworkResult.Success) {
                latestSuccess = result.data
            }
        }

        if (latestSuccess != null) {
            upNextWatchNextSyncer.syncSeriesCatalog(latestSuccess)
        }
    }

    private fun isUpNextSeriesCatalog(catalog: CatalogDescriptor): Boolean {
        return catalog.name.equals("Up Next", ignoreCase = true) &&
            catalog.apiType.equals("series", ignoreCase = true)
    }
}
