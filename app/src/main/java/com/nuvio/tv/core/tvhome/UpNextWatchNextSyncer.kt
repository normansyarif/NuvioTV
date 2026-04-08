package com.nuvio.tv.core.tvhome

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context

@Singleton
class UpNextWatchNextSyncer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "UpNextWatchNextSyncer"
        private const val MANAGED_PREFIX = "nuvio_up_next_series:"
    }

    suspend fun syncSeriesCatalog(catalogRow: CatalogRow) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return@withContext
        }

        val resolver = context.contentResolver
        val existingPrograms = queryManagedPrograms(resolver).toMutableMap()
        val desiredInternalIds = linkedSetOf<String>()
        val baseEngagementTime = System.currentTimeMillis()

        catalogRow.items.forEachIndexed { index, item ->
            if (!item.apiType.equals("series", ignoreCase = true)) {
                return@forEachIndexed
            }

            val internalProviderId = buildInternalProviderId(catalogRow, item)
            if (!desiredInternalIds.add(internalProviderId)) {
                return@forEachIndexed
            }

            val existingProgram = existingPrograms.remove(internalProviderId)?.takeUnless { program ->
                if (program.isBrowsable) {
                    false
                } else {
                    deleteProgram(resolver, program.id)
                    true
                }
            }

            val desiredProgram = buildProgram(
                existingProgram = existingProgram,
                catalogRow = catalogRow,
                item = item,
                lastEngagementTimeUtcMillis = baseEngagementTime - (index * 1000L)
            )

            when {
                existingProgram == null -> insertProgram(resolver, desiredProgram, internalProviderId)
                existingProgram.hasAnyUpdatedValues(desiredProgram) -> {
                    updateProgram(resolver, existingProgram.id, desiredProgram, internalProviderId)
                }
            }
        }

        existingPrograms.values.forEach { staleProgram ->
            deleteProgram(resolver, staleProgram.id)
        }
    }

    private fun queryManagedPrograms(resolver: ContentResolver): Map<String, WatchNextProgram> {
        val programs = linkedMapOf<String, WatchNextProgram>()
        resolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            WatchNextProgram.PROJECTION,
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val program = WatchNextProgram.fromCursor(cursor)
                val internalProviderId = program.internalProviderId ?: continue
                if (internalProviderId.startsWith(MANAGED_PREFIX)) {
                    programs[internalProviderId] = program
                }
            }
        }
        return programs
    }

    private fun buildProgram(
        existingProgram: WatchNextProgram?,
        catalogRow: CatalogRow,
        item: MetaPreview,
        lastEngagementTimeUtcMillis: Long
    ): WatchNextProgram {
        val posterUri = item.backdropUrl.toUriOrNull() ?: item.poster.toUriOrNull()
        val thumbnailUri = item.backdropUrl.toUriOrNull()
        val logoUri = item.logo.toUriOrNull()
        val description = item.description
            ?.takeIf { it.isNotBlank() }
            ?: item.releaseInfo?.takeIf { it.isNotBlank() }
            ?: catalogRow.catalogName
        val builder = if (existingProgram != null) {
            WatchNextProgram.Builder(existingProgram)
        } else {
            WatchNextProgram.Builder()
        }

        builder
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT)
            .setType(TvContractCompat.PreviewPrograms.TYPE_TV_SERIES)
            .setTitle(item.name)
            .setDescription(description)
            .setIntent(buildLaunchIntent(item, catalogRow.addonBaseUrl))
            .setInternalProviderId(buildInternalProviderId(catalogRow, item))
            .setContentId(item.id)
            .setSeriesId(item.id)
            .setAvailability(TvContractCompat.PreviewPrograms.AVAILABILITY_FREE)
            .setLastEngagementTimeUtcMillis(lastEngagementTimeUtcMillis)

        if (posterUri != null) {
            builder
                .setPosterArtUri(posterUri)
                .setPosterArtAspectRatio(
                    if (!item.backdropUrl.isNullOrBlank()) {
                        TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9
                    } else {
                        TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3
                    }
                )
        }

        if (thumbnailUri != null) {
            builder
                .setThumbnailUri(thumbnailUri)
                .setThumbnailAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
        }

        if (logoUri != null) {
            builder.setLogoUri(logoUri)
        }

        val genre = item.genres
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(3)
            .joinToString(", ")
        if (genre.isNotBlank()) {
            builder.setGenre(genre)
        }

        return builder.build()
    }

    private fun buildLaunchIntent(item: MetaPreview, addonBaseUrl: String): Intent {
        val detailUri = Uri.Builder()
            .scheme("nuvio")
            .authority("detail")
            .appendPath(item.id)
            .appendPath(item.apiType)
            .appendQueryParameter("addonBaseUrl", addonBaseUrl)
            .build()

        return Intent(Intent.ACTION_VIEW, detailUri).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
            `package` = context.packageName
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    private fun insertProgram(
        resolver: ContentResolver,
        program: WatchNextProgram,
        internalProviderId: String
    ) {
        resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues())
        Log.d(TAG, "Inserted watch next program internalProviderId=$internalProviderId")
    }

    private fun updateProgram(
        resolver: ContentResolver,
        programId: Long,
        program: WatchNextProgram,
        internalProviderId: String
    ) {
        resolver.update(
            TvContractCompat.buildWatchNextProgramUri(programId),
            program.toContentValues(),
            null,
            null
        )
        Log.d(TAG, "Updated watch next program internalProviderId=$internalProviderId")
    }

    private fun deleteProgram(resolver: ContentResolver, programId: Long) {
        if (programId <= 0L) {
            return
        }
        resolver.delete(TvContractCompat.buildWatchNextProgramUri(programId), null, null)
    }

    private fun buildInternalProviderId(catalogRow: CatalogRow, item: MetaPreview): String {
        return "$MANAGED_PREFIX${catalogRow.addonId}:${catalogRow.catalogId}:${item.id}"
    }

    private fun String?.toUriOrNull(): Uri? = this
        ?.takeIf { it.isNotBlank() }
        ?.let(Uri::parse)
}
