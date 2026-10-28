@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.nuvio.tv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.ui.util.BlurTransformation
import com.nuvio.tv.ui.components.ImdbRatingSourceLabel

/** Full-screen episode browser. It shares the detail session's progress and season state. */
@Composable
internal fun CinematicEpisodesPage(
    meta: Meta, seasons: List<Int>, selectedSeason: Int, episodes: List<Video>,
    nextToWatch: NextToWatch?, progress: Map<Pair<Int, Int>, WatchProgress>,
    watched: Set<Pair<Int, Int>>, pending: Set<String>, ratings: Map<Pair<Int, Int>, Double>,
    blurUnwatched: Boolean, overlayStyle: EpisodeOptionsOverlayStyle,
    lastFocusedBySeason: MutableMap<Int, String>,
    onSeasonSelected: (Int) -> Unit, onBack: () -> Unit,
    onPlay: (Video) -> Unit, canPlay: (Video) -> Boolean,
    onStartFromBeginning: (Video) -> Unit,
    onToggleWatched: (Video) -> Unit, onMarkSeasonWatched: (Int) -> Unit,
    onMarkSeasonUnwatched: (Int) -> Unit, isSeasonFullyWatched: (Int) -> Boolean,
    onMarkPreviousWatched: (Video) -> Unit,
    onOpenComments: (Video) -> Unit, showComments: Boolean,
    returnSeason: Int? = null, returnEpisode: Int? = null,
    onReturnConsumed: () -> Unit = {}
) {
    var options by remember { mutableStateOf<Video?>(null) }
    val list = rememberLazyListState()
    val seasonList = rememberLazyListState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val seasonRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val scope = rememberCoroutineScope()
    var seasonPaneFocused by remember { mutableStateOf(true) }
    var initialFocusPlaced by remember(meta.id) { mutableStateOf(false) }
    val latestEpisodes by rememberUpdatedState(episodes)
    val latestSelectedSeason by rememberUpdatedState(selectedSeason)
    fun enterEpisodes() {
        scope.launch {
            val currentEpisodes = latestEpisodes
            val target = currentEpisodes.firstOrNull { it.id == lastFocusedBySeason[latestSelectedSeason] }
                ?: currentEpisodes.firstOrNull() ?: return@launch
            val requester = requesters.getOrPut(target.id) { FocusRequester() }
            list.scrollToItem(currentEpisodes.indexOf(target))
            repeat(12) {
                withFrameNanos { }
                if (runCatching { requester.requestFocus(FocusDirection.Enter) }.getOrDefault(false)) return@launch
            }
        }
    }
    fun focusSeason(season: Int) {
        seasonPaneFocused = true
        scope.launch {
            val index = sortedSeasonIndex(seasons, season)
            if (seasonList.layoutInfo.visibleItemsInfo.none { it.index == index }) seasonList.scrollToItem(index)
            val requester = seasonRequesters.getOrPut(season) { FocusRequester() }
            repeat(12) {
                withFrameNanos { }
                if (runCatching { requester.requestFocus(FocusDirection.Enter) }.getOrDefault(false)) return@launch
            }
        }
    }
    val backRequester = remember { FocusRequester() }
    val sortedSeasons = remember(seasons) { seasons.filter { it > 0 }.sorted() + seasons.filter { it == 0 } }
    var logoFailed by remember(meta.logo) { mutableStateOf(false) }
    var initialSeasonChosen by rememberSaveable(meta.id) { mutableStateOf(false) }
    var focusedEpisode by rememberSaveable(meta.id) { mutableStateOf<String?>(null) }
    BackHandler(enabled = options == null, onBack = onBack)
    LaunchedEffect(episodes.isEmpty()) {
        if (episodes.isEmpty() && seasons.isEmpty()) { withFrameNanos { }; backRequester.requestFocus() }
    }
    LaunchedEffect(meta.id, returnSeason, nextToWatch?.nextSeason, seasons) {
        if (!initialSeasonChosen && seasons.isNotEmpty()) {
            val preferred = returnSeason ?: if (lastFocusedBySeason.isEmpty()) nextToWatch?.nextSeason else selectedSeason
            if (preferred != null && preferred in seasons && preferred != selectedSeason) onSeasonSelected(preferred)
            initialSeasonChosen = true
        }
    }
    LaunchedEffect(selectedSeason, episodes.map { it.id }, returnSeason, returnEpisode) {
        if (episodes.isEmpty()) return@LaunchedEffect
        val returningToEpisode = returnSeason == selectedSeason && returnEpisode != null
        if (returningToEpisode) {
            val target = episodes.firstOrNull { it.episode == returnEpisode } ?: episodes.first()
            list.scrollToItem(episodes.indexOf(target))
            val requester = requesters.getOrPut(target.id) { FocusRequester() }
            repeat(12) {
                withFrameNanos { }
                if (runCatching { requester.requestFocus(FocusDirection.Enter) }.getOrDefault(false)) {
                    onReturnConsumed()
                    initialFocusPlaced = true
                    return@LaunchedEffect
                }
            }
        } else if (!initialFocusPlaced) {
            initialFocusPlaced = true
            focusSeason(selectedSeason)
        } else if (seasonPaneFocused) {
            // Refresh the list when the selected season changes, without moving
            // focus out of the season pane. Only Right/Select enters episodes.
            val index = episodes.indexOfFirst { it.id == lastFocusedBySeason[selectedSeason] }.coerceAtLeast(0)
            list.scrollToItem(index)
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF080808)).semantics { testTagsAsResourceId = true }) {
        AsyncImage(meta.backdropUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .10f)
        Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
            Column(Modifier.width(278.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(onClick = onBack, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.focusRequester(backRequester).testTag("episodes_back")) {
                    Text("‹  Back to details", fontSize = 14.sp)
                }
                Text("S E R I E S", color = Color(0xFFE50914), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (meta.logo != null && !logoFailed) AsyncImage(meta.logo, meta.name,
                    Modifier.fillMaxWidth().height(82.dp), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                    onError = { logoFailed = true })
                else Text(meta.name, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold, maxLines = 3)
                Text(listOfNotNull(meta.releaseInfo, "${seasons.count { it > 0 }} ${if (seasons.count { it > 0 } == 1) "Season" else "Seasons"}", meta.ageRating).joinToString("  •  "),
                    fontSize = 12.sp, color = Color(0xFFB3B3B3))
                LazyColumn(state = seasonList, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    items(sortedSeasons, key = { it }) { season ->
                        val requester = seasonRequesters.getOrPut(season) { FocusRequester() }
                        Button(onClick = { onSeasonSelected(season); enterEpisodes() },
                            modifier = Modifier.fillMaxWidth().focusRequester(requester)
                                .onPreviewKeyEvent { event ->
                                    when (event.key) {
                                        Key.DirectionRight -> {
                                            if (event.type == KeyEventType.KeyDown) enterEpisodes()
                                            true
                                        }
                                        Key.DirectionUp, Key.DirectionDown -> {
                                            if (event.type == KeyEventType.KeyDown) {
                                                val next = adjacentEpisodeSeason(sortedSeasons, season,
                                                    if (event.key == Key.DirectionDown) 1 else -1)
                                                if (next != null) focusSeason(next)
                                                else if (event.key == Key.DirectionUp) backRequester.requestFocus()
                                            }
                                            true
                                        }
                                        Key.DirectionLeft -> true
                                        else -> false
                                    }
                                }
                                .onFocusChanged {
                                    if (it.isFocused) seasonPaneFocused = true
                                    if (it.isFocused && season != selectedSeason) {
                                        focusedEpisode = null
                                        onSeasonSelected(season)
                                    }
                                }
                                .testTag("episodes_season_$season"),
                            shape = ButtonDefaults.shape(RoundedCornerShape(4.dp)),
                            scale = ButtonDefaults.scale(focusedScale = 1f),
                            colors = ButtonDefaults.colors(
                                containerColor = if (season == selectedSeason) Color(0xFF303030) else Color.Transparent,
                                contentColor = Color.White, focusedContainerColor = Color(0xFFE50914), focusedContentColor = Color.White),
                            contentPadding = PaddingValues(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (season == 0) "Specials" else "Season $season", fontSize = 16.sp)
                                val count = meta.videos.count { it.season == season }
                                if (count > 0) Text("$count episodes", fontSize = 12.sp, color = Color(0xFFB3B3B3))
                            }
                        }
                    }
                }
                Text("Hold an episode for more options", color = Color(0xFF808080), fontSize = 11.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (selectedSeason == 0) "Specials" else "Season $selectedSeason",
                        color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                    Text("${episodes.size} episodes", color = Color(0xFFB3B3B3), fontSize = 13.sp)
                }
                if (episodes.isEmpty()) Text("No episodes available for this season", color = Color(0xFFB3B3B3), fontSize = 16.sp)
                LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                    itemsIndexed(episodes, key = { _, video -> video.id }) { index, video ->
                        val requester = requesters.getOrPut(video.id) { FocusRequester() }
                        val key = (video.season ?: selectedSeason) to (video.episode ?: 0)
                        val watchProgress = progress[key]
                        val isWatched = key in watched || watchProgress?.isCompleted() == true
                        EpisodeListEntry(video, index, isWatched, watchProgress, ratings[key], blurUnwatched,
                            modifier = Modifier.focusRequester(requester)
                                .onPreviewKeyEvent { event ->
                                    if (event.key == Key.DirectionLeft) {
                                        if (event.type == KeyEventType.KeyDown) focusSeason(selectedSeason)
                                        true
                                    } else false
                                }
                                .focusProperties { left = FocusRequester.Cancel }
                                .onFocusChanged { if (it.isFocused) {
                                    seasonPaneFocused = false
                                    focusedEpisode = video.id
                                    lastFocusedBySeason[selectedSeason] = video.id
                                } }
                                .testTag("episodes_item_${video.episode ?: index + 1}"),
                            onClick = { if (canPlay(video)) onPlay(video) }, onLongClick = { options = video })
                    }
                }
            }
        }
    }
    options?.let { episode ->
        val key = (episode.season ?: selectedSeason) to (episode.episode ?: 0)
        EpisodeOptionsOverlay(episode = episode, imdbRating = ratings[key],
            isWatched = key in watched || progress[key]?.isCompleted() == true,
            blurUnwatchedEpisodes = blurUnwatched, style = overlayStyle,
            isPending = "${episode.id}:${episode.season ?: -1}:${episode.episode ?: -1}" in pending,
            isSeasonFullyWatched = isSeasonFullyWatched(episode.season ?: selectedSeason),
            hasPreviousEpisodes = episodes.indexOfFirst { it.id == episode.id } > 0,
            hasProgress = progress[key]?.progressPercentage?.let { it > 0f && it < 1f } == true,
            onDismiss = { options = null }, isPlayEnabled = canPlay(episode),
            onPlay = { options = null; onPlay(episode) },
            onStartFromBeginning = { options = null; onStartFromBeginning(episode) },
            onOpenEpisodeComments = { options = null; onOpenComments(episode) }, showOpenEpisodeComments = showComments,
            onToggleWatched = { onToggleWatched(episode) },
            onMarkSeasonWatched = { onMarkSeasonWatched(episode.season ?: selectedSeason) },
            onMarkSeasonUnwatched = { onMarkSeasonUnwatched(episode.season ?: selectedSeason) },
            onMarkPreviousEpisodesWatched = { onMarkPreviousWatched(episode) },
            onNavigateEpisode = { step -> adjacentEpisode(episodes, episode, step)?.let { options = it } })
    }
}

@Composable
private fun EpisodeListEntry(video: Video, index: Int, watched: Boolean, progress: WatchProgress?,
    rating: Double?, blurUnwatched: Boolean, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val image = remember(video.thumbnail, blurUnwatched, watched) {
        ImageRequest.Builder(context).data(video.thumbnail).apply {
            if (blurUnwatched && !watched) transformations(BlurTransformation())
        }.build()
    }
    Card(onClick = onClick, onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(154.dp),
        scale = CardDefaults.scale(focusedScale = 1f), shape = CardDefaults.shape(RoundedCornerShape(5.dp)),
        colors = CardDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color(0xFF222222)),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(5.dp)))) {
        Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(198.dp).height(112.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF242424))) {
                AsyncImage(image, video.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                rating?.let { episodeRating ->
                    Row(Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .background(Color(0xE6080808), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp).testTag("episode_imdb_rating"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        ImdbRatingSourceLabel(logoModifier = Modifier.width(30.dp).height(15.dp),
                            textStyle = MaterialTheme.typography.labelSmall, textColor = Color.White)
                        Text("%.1f".format(episodeRating), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                val percent = progress?.progressPercentage ?: if (watched) 1f else 0f
                if (percent > 0f) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0xFF666666)))
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(percent.coerceIn(.001f, 1f)).height(4.dp).background(Color(0xFFE50914)))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${video.episode ?: index + 1}. ${video.title}", color = Color.White, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                video.overview?.let { Text(it, color = Color(0xFFD3D3D3), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                Text(listOfNotNull(video.runtime?.let { "${it}m" }, video.released?.take(10),
                    if (watched) "✓ Watched" else null).joinToString("  •  "),
                    color = Color(0xFFB3B3B3), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
