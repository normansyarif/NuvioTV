@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.nuvio.tv.ui.screens.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.nuvio.tv.LocalCinemaContentType
import com.nuvio.tv.LocalCinemaPlay
import com.nuvio.tv.LocalCinemaHeaderFocusRequester
import kotlinx.coroutines.channels.Channel
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.components.ContinueWatchingOptionsDialog

private val CinemaRed = Color(0xFFE50914)
private val CinemaBlack = Color(0xFF080808)

@Composable
fun CinematicHomeContent(
    uiState: HomeUiState, presentation: ModernHomePresentationState,
    focusState: HomeScreenFocusState, scrollToTopTrigger: Int,
    enrichedPreviews: Map<String, MetaPreview>,
    onNavigateToDetail: (String, String, String) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onContinueWatchingStartFromBeginning: (ContinueWatchingItem) -> Unit,
    onContinueWatchingPlayManually: (ContinueWatchingItem) -> Unit,
    showManualPlay: Boolean,
    isCatalogItemWatched: (MetaPreview) -> Boolean,
    onRemoveContinueWatching: (String, Int?, Int?, Boolean) -> Unit,
    onNavigateToFolderDetail: (String, String) -> Unit,
    onCatalogItemLongPress: (MetaPreview, String) -> Unit,
    onItemFocus: (MetaPreview) -> Unit,
    onLoadMore: (String, String, String) -> Unit,
    onRequestLazyCatalogLoad: (String) -> Unit,
    onSaveFocus: (Int, Int, String?, Map<String, String>, Map<String, Int>, Map<String, String>, Int, Int) -> Unit
) {
    val filter = LocalCinemaContentType.current
    val play = LocalCinemaPlay.current
    val headerRequester = LocalCinemaHeaderFocusRequester.current
    val viewportWidth = LocalConfiguration.current.screenWidthDp.dp
    val rowEndPadding = (viewportWidth - 36.dp - 160.dp).coerceAtLeast(36.dp)
    val rows = remember(presentation.rows, filter) {
        presentation.rows.list.mapNotNull { row ->
            val items = row.items.list.filter { item ->
                filter == null || when (val payload = item.payload) {
                    is ModernPayload.Catalog -> payload.itemType == filter
                    is ModernPayload.ContinueWatching -> item.heroPreview.isSeries == (filter == "series")
                    is ModernPayload.CollectionFolder -> true
                }
            }
            if (items.isEmpty()) null else row.copy(items = com.nuvio.tv.ui.util.StableList(items))
        }
    }
    val hero = uiState.heroItems.firstOrNull {
        (filter == null || it.apiType == filter) && cinematicBackdropUrl(it.background, it.landscapePoster, it.poster, it.rawPosterUrl) != null
    }
    val failedBackdrops = remember { mutableStateMapOf<String, Boolean>() }
    fun availableBackdrop(item: ModernCarouselItem) = item.cinematicBackdropUrl()?.takeUnless { failedBackdrops[it] == true }
    val column = rememberLazyListState(focusState.verticalScrollIndex, focusState.verticalScrollOffset)
    val rowStates = remember { mutableMapOf<String, LazyListState>() }
    val focusedKeys = remember { focusState.focusedItemKeyByRow.toMutableMap() }
    var focusedRow by remember { mutableStateOf(focusState.focusedRowKey) }
    var options by remember { mutableStateOf<ContinueWatchingItem?>(null) }
    val heroRequester = remember { FocusRequester() }
    val cardRequesters = remember { mutableMapOf<Pair<String, String>, FocusRequester>() }
    fun cardRequester(rowKey: String, itemKey: String) =
        cardRequesters.getOrPut(rowKey to itemKey) { FocusRequester() }
    val verticalMoves = remember(filter) { Channel<Int>(Channel.UNLIMITED) }
    val latestRows by rememberUpdatedState(rows)
    val latestHero by rememberUpdatedState(hero)
    fun Modifier.cinemaNavigation(horizontal: Boolean = true) = onPreviewKeyEvent { event ->
        val direction = when (event.key) {
            Key.DirectionUp -> -1
            Key.DirectionDown -> 1
            Key.DirectionLeft -> if (horizontal) -2 else return@onPreviewKeyEvent false
            Key.DirectionRight -> if (horizontal) 2 else return@onPreviewKeyEvent false
            else -> return@onPreviewKeyEvent false
        }
        if (event.type == KeyEventType.KeyDown) verticalMoves.trySend(direction)
        true
    }
    LaunchedEffect(verticalMoves) {
        for (direction in verticalMoves) {
            val currentRows = latestRows
            val currentRow = currentRows.indexOfFirst { it.key == focusedRow }
            val selectedColumn = currentRows.getOrNull(currentRow)?.items?.list
                ?.indexOfFirst { it.key == focusedKeys[focusedRow] }?.coerceAtLeast(0) ?: 0
            val position = CinematicFocusPosition(currentRow, selectedColumn)
            val horizontalMove = kotlin.math.abs(direction) == 2
            val target = if (horizontalMove) {
                position.moveHorizontally(direction / 2, currentRows.map { it.items.size })
            } else {
                val rowColumns = currentRows.map { row ->
                    // Each destination owns its selection. If it has no remembered
                    // tile, use the start of its existing window, not the source column.
                    row.items.list.indexOfFirst { it.key == focusedKeys[row.key] }
                        .takeIf { it >= 0 }
                        ?: rowStates[row.key]?.firstVisibleItemIndex
                        ?: focusState.catalogRowScrollStates[row.key]
                        ?: 0
                }
                position.moveVertically(direction, currentRows.map { it.items.size }, latestHero != null, rowColumns)
            }
            if (horizontalMove && target == position) continue
            when {
                target.row == -2 -> {
                    column.scrollToItem(0)
                    headerRequester.requestFocus()
                }
                target.row == -1 -> {
                    focusedRow = null
                    column.scrollToItem(0)
                    withFrameNanos { }
                    heroRequester.requestFocus()
                }
                else -> {
                    val row = currentRows.getOrNull(target.row) ?: continue
                    val item = row.items.list.getOrNull(target.column) ?: continue
                    val requester = cardRequester(row.key, item.key)
                    val rowState = rowStates.getOrPut(row.key) { LazyListState() }
                    // Both lists move under one fixed selection anchor. Trailing
                    // padding also lets the final tile occupy this same position.
                    if (!horizontalMove) column.scrollToItem(target.row + if (latestHero != null) 1 else 0)
                    withFrameNanos { }
                    val destinationVisible = rowState.layoutInfo.visibleItemsInfo.any { it.index == target.column }
                    if (!destinationVisible) rowState.scrollToItem(target.column)
                    for (attempt in 0 until 30) {
                        withFrameNanos { }
                        if (runCatching { requester.requestFocus(FocusDirection.Enter) }.getOrDefault(false)) break
                    }
                    // Wait for the selected card to acquire its landscape width,
                    // then slide it to the exact same left inset on every move.
                    withFrameNanos { }
                    if (horizontalMove) rowState.animateScrollToItem(target.column)
                    else rowState.scrollToItem(target.column)
                }
            }
        }
    }
    var restored by remember { mutableStateOf(false) }
    fun saveFocus() {
        onSaveFocus(column.firstVisibleItemIndex, column.firstVisibleItemScrollOffset,
            focusedRow, focusedKeys.toMap(), rowStates.mapValues { it.value.firstVisibleItemIndex },
            emptyMap(), rows.indexOfFirst { it.key == focusedRow }.coerceAtLeast(0), 0)
    }
    val latestSave by rememberUpdatedState({ saveFocus() })
    DisposableEffect(Unit) { onDispose { latestSave() } }
    LaunchedEffect(filter, scrollToTopTrigger) {
        if (filter != null || scrollToTopTrigger > 0) column.scrollToItem(0)
    }
    LaunchedEffect(rows.isNotEmpty(), hero != null) {
        if (!restored && (hero != null || rows.isNotEmpty())) {
            withFrameNanos { }
            if (focusState.hasSavedFocus && rows.any { row -> row.items.list.any { it.key == focusedKeys[focusedRow] } }) {
                val row = rows.first { it.key == focusedRow }
                val key = focusedKeys[row.key] ?: return@LaunchedEffect
                runCatching { cardRequester(row.key, key).requestFocus() }
            } else if (hero != null) runCatching { heroRequester.requestFocus() }
            restored = true
        }
    }
    // Vertical scrolling follows logical row navigation, never the geometric
    // center of an expanding card or the hero's low-positioned action buttons.
    val rowBringSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides rowBringSpec) {
        LazyColumn(state = column, modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
            contentPadding = PaddingValues(bottom = 50.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (hero != null) item(key = "cinema_hero") {
                val enriched = enrichedPreviews[hero.id] ?: hero
                CinemaHero(enriched, heroRequester,
                    navigationModifier = Modifier.cinemaNavigation(horizontal = false),
                    onFocused = { focusedRow = null },
                    onPlay = { saveFocus(); play(hero.id, hero.apiType, "") },
                    onDetails = { saveFocus(); onNavigateToDetail(hero.id, hero.apiType, "") })
            }
            itemsIndexed(rows, key = { _, row -> row.key }) { rowIndex, row ->
                val rowState = remember(row.key) {
                    rowStates.getOrPut(row.key) { LazyListState(focusState.catalogRowScrollStates[row.key] ?: 0) }
                }
                var selectedKey by remember(row.key) { mutableStateOf(focusedKeys[row.key]) }
                var selectedIndex by remember(row.key) { mutableIntStateOf(0) }
                val selected = cinematicSelectedItem(row.items.list, selectedKey, selectedIndex)
                val active = focusedRow == row.key
                var rowHasFocus by remember(row.key) { mutableStateOf(false) }
                val lazyLoadKey = row.cinematicLazyLoadKey()
                LaunchedEffect(lazyLoadKey) { lazyLoadKey?.let(onRequestLazyCatalogLoad) }
                LaunchedEffect(row.isLoading, selected.key) {
                    if (active && !row.isLoading && selected.key != selectedKey) {
                        selectedKey = selected.key
                        focusedKeys[row.key] = selected.key
                        rowState.scrollToItem(row.items.list.indexOf(selected))
                        repeat(12) {
                            withFrameNanos { }
                            if (runCatching { cardRequester(row.key, selected.key).requestFocus(FocusDirection.Enter) }.getOrDefault(false)) return@LaunchedEffect
                        }
                    }
                }
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 36.dp))
                    Box {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides rowBringSpec) {
                    LazyRow(modifier = Modifier.onFocusChanged { rowHasFocus = it.hasFocus }.focusGroup(), state = rowState, contentPadding = PaddingValues(start = 36.dp, end = rowEndPadding, top = 5.dp, bottom = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(row.items.list, key = { _, item -> item.key }) { index, item ->
                            CinemaCard(item, active && selected.key == item.key,
                                backdropUrl = availableBackdrop(item),
                                onBackdropFailed = { failedBackdrops[it] = true },
                                watched = item.metaPreview?.let(isCatalogItemWatched) == true,
                                modifier = Modifier.focusRequester(cardRequester(row.key, item.key))
                                    .testTag("cinema_row_${rowIndex}_item_${index}")
                                    .cinemaNavigation(),
                                onFocused = {
                                    focusedRow = row.key; selectedKey = item.key; selectedIndex = index; focusedKeys[row.key] = item.key
                                    item.metaPreview?.let(onItemFocus)
                                    if (index >= row.items.list.size - 5 && row.hasMore && !row.isLoading) {
                                        row.catalogId?.let { onLoadMore(it, row.addonId.orEmpty(), row.apiType.orEmpty()) }
                                    }
                                }, onClick = {
                                    if (row.isLoading) { lazyLoadKey?.let(onRequestLazyCatalogLoad); return@CinemaCard }
                                    saveFocus()
                                    when (val payload = item.payload) {
                                        is ModernPayload.Catalog -> onNavigateToDetail(payload.itemId, payload.itemType, payload.addonBaseUrl)
                                        is ModernPayload.ContinueWatching -> onContinueWatchingClick(payload.item)
                                        is ModernPayload.CollectionFolder -> onNavigateToFolderDetail(payload.collectionId, payload.folderId)
                                    }
                                }, onLongClick = {
                                    when (val payload = item.payload) {
                                        is ModernPayload.ContinueWatching -> options = payload.item
                                        is ModernPayload.Catalog -> item.metaPreview?.let { onCatalogItemLongPress(it, payload.addonBaseUrl) }
                                        else -> Unit
                                    }
                                })
                        }
                    }
                    }
                    if (rowHasFocus) Box(Modifier.padding(start = 36.dp, top = 5.dp)
                        .size(if (availableBackdrop(selected) != null) 440.dp else 160.dp, 248.dp).border(2.dp, Color.White, RoundedCornerShape(5.dp))
                        .testTag("cinema_selection_anchor"))
                    }
                    if (active) {
                        val preview = selected.heroPreview
                        Column(Modifier.padding(horizontal = 36.dp).widthIn(max = 570.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(listOfNotNull(preview.contentTypeText, preview.yearText, preview.runtimeText,
                                preview.ageRatingText, preview.imdbText?.let { "IMDb $it" }).joinToString("  •  "),
                                color = Color(0xFFB3B3B3), fontSize = 12.sp, maxLines = 1)
                            Text(cinematicDescription(preview.description, selected.title), color = Color(0xFFD6D6D6), fontSize = 13.sp,
                                lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (rows.isEmpty() && hero == null) item {
                Text("No titles available in this category", color = Color(0xFFB3B3B3), modifier = Modifier.padding(36.dp))
            }
        }
    }
    options?.let { item ->
        val id = when (item) { is ContinueWatchingItem.InProgress -> item.progress.contentId; is ContinueWatchingItem.NextUp -> item.info.contentId }
        val type = when (item) { is ContinueWatchingItem.InProgress -> item.progress.contentType; is ContinueWatchingItem.NextUp -> item.info.contentType }
        ContinueWatchingOptionsDialog(item, onDismiss = { options = null }, onRemove = {
            when (item) {
                is ContinueWatchingItem.InProgress -> onRemoveContinueWatching(id, item.progress.season, item.progress.episode, false)
                is ContinueWatchingItem.NextUp -> onRemoveContinueWatching(id, item.info.season, item.info.episode, true)
            }; options = null
        }, onDetails = { options = null; saveFocus(); onNavigateToDetail(id, type, "") },
            onStartFromBeginning = { options = null; onContinueWatchingStartFromBeginning(item) },
            showPlayManually = showManualPlay, onPlayManually = { options = null; onContinueWatchingPlayManually(item) })
    }
}

@Composable
private fun CinemaHero(item: MetaPreview, requester: FocusRequester, navigationModifier: Modifier, onFocused: () -> Unit, onPlay: () -> Unit, onDetails: () -> Unit) {
    var logoFailed by remember(item.logo) { mutableStateOf(false) }
    Box(navigationModifier.padding(horizontal = 32.dp).fillMaxWidth().height(332.dp).clip(RoundedCornerShape(10.dp))) {
        AsyncImage(item.backdropUrl ?: item.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .92f), Color.Black.copy(alpha = .55f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .6f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp).width(410.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (item.apiType == "series") "S E R I E S" else "F I L M", color = CinemaRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (item.logo != null && !logoFailed) AsyncImage(item.logo, item.name, Modifier.width(260.dp).height(70.dp),
                contentScale = ContentScale.Fit, alignment = Alignment.CenterStart, onError = { logoFailed = true })
            else Text(item.name, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(item.genres.take(2).joinToString(" • ").takeIf { it.isNotBlank() }, item.releaseInfo,
                item.imdbRating?.let { "IMDb $it" }).joinToString("  •  "), color = Color(0xFFDEDEDE), fontSize = 12.sp, maxLines = 1)
            Text(cinematicDescription(item.description, item.name), color = Color.White, fontSize = 14.sp,
                lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onPlay, modifier = Modifier.focusRequester(requester).testTag("cinema_hero_play")
                    .onFocusChanged { if (it.isFocused) onFocused() },
                    colors = ButtonDefaults.colors(containerColor = Color.White, contentColor = Color.Black,
                        focusedContainerColor = CinemaRed, focusedContentColor = Color.White),
                    shape = ButtonDefaults.shape(RoundedCornerShape(24.dp)), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)) {
                    Text("▶  Play", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = onDetails, modifier = Modifier.onFocusChanged { if (it.isFocused) onFocused() },
                    colors = ButtonDefaults.colors(containerColor = Color(0xBB454545), contentColor = Color.White,
                        focusedContainerColor = Color.White, focusedContentColor = Color.Black),
                    shape = ButtonDefaults.shape(RoundedCornerShape(24.dp)), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)) {
                    Text("ⓘ  More Info", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun CinemaCard(item: ModernCarouselItem, expanded: Boolean, backdropUrl: String?, onBackdropFailed: (String) -> Unit,
    watched: Boolean, modifier: Modifier,
    onFocused: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val wide = expanded && backdropUrl != null
    val width = if (wide) 440.dp else 160.dp
    val preview = item.heroPreview
    Card(onClick = onClick, onLongClick = onLongClick,
        modifier = modifier.width(width).height(248.dp).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() },
        shape = CardDefaults.shape(RoundedCornerShape(5.dp)),
        scale = CardDefaults.scale(focusedScale = 1f),
        border = CardDefaults.border(focusedBorder = Border.None)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF202020))) {
            AsyncImage(if (wide) backdropUrl else preview.poster ?: item.metaPreview?.poster ?: item.imageUrl,
                item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                onError = { if (wide && backdropUrl != null) onBackdropFailed(backdropUrl) })
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .85f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (wide && preview.logo != null) AsyncImage(preview.logo, item.title, Modifier.width(210.dp).height(58.dp),
                    contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
                else Text(item.title, color = Color.White, fontSize = if (wide) 18.sp else 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (wide) item.subtitle?.let { Text(it, color = Color(0xFFDEDEDE), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (watched) Text("✓", color = Color.White, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    .background(CinemaRed, RoundedCornerShape(12.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
            val progress = ((item.payload as? ModernPayload.ContinueWatching)?.item as? ContinueWatchingItem.InProgress)?.progress?.progressPercentage
            if (progress != null) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0xFF666666)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(.001f, 1f)).height(3.dp).background(CinemaRed))
            }
        }
    }
}
