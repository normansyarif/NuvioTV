package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.catalogRowLegacyKey

/** UI rows use URL-qualified identity; the pending catalog queue uses the legacy key. */
internal fun HeroCarouselRow.cinematicLazyLoadKey(): String? {
    if (!isLoading) return null
    return catalogRowLegacyKey(addonId ?: return null, apiType ?: return null, catalogId ?: return null)
}

internal fun cinematicSelectedItem(items: List<ModernCarouselItem>, key: String?, index: Int): ModernCarouselItem =
    items.firstOrNull { it.key == key } ?: items[index.coerceIn(0, items.lastIndex)]
