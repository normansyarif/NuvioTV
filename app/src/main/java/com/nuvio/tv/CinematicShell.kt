package com.nuvio.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.nuvio.tv.ui.navigation.Screen

val LocalCinemaHeaderFocusRequester = compositionLocalOf { FocusRequester.Default }
val LocalCinemaContentType = compositionLocalOf<String?> { null }
val LocalCinemaPlay = compositionLocalOf<(String, String, String) -> Unit> { { _, _, _ -> } }

/** Root navigation stays visible while browsing; immersive detail/player routes use the full screen. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinematicShell(
    currentRoute: String?, rootRoutes: Set<String>, activeProfileName: String,
    activeProfileAvatar: String?, onSwitchProfile: () -> Unit,
    onNavigate: (String) -> Unit, onPlay: (String, String, String) -> Unit,
    onExit: () -> Unit, content: @Composable () -> Unit
) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val showNavigation = currentRoute in rootRoutes
    val contentRequester = remember { FocusRequester() }
    val homeRequester = remember { FocusRequester() }
    var headerFocused by remember { mutableStateOf(false) }
    BackHandler(enabled = showNavigation) {
        if (headerFocused) onExit() else homeRequester.requestFocus()
    }
    CompositionLocalProvider(
        LocalCinemaHeaderFocusRequester provides homeRequester,
        LocalCinemaContentType provides filter,
        LocalCinemaPlay provides onPlay,
        LocalContentFocusRequester provides contentRequester,
        LocalSidebarExpanded provides false
    ) {
        Column(Modifier.fillMaxSize().background(Color(0xFF080808))) {
            if (showNavigation) {
                Row(
                    Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 32.dp)
                        .onFocusChanged { headerFocused = it.hasFocus },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CinemaNavButton(activeProfileName.take(12), false, onSwitchProfile) {
                        if (activeProfileAvatar != null) AsyncImage(activeProfileAvatar, null,
                            Modifier.size(24.dp), contentScale = ContentScale.Crop)
                        else Image(painterResource(R.drawable.cinema_logo), null, Modifier.size(24.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    CinemaNavButton("Search", currentRoute == Screen.Search.route,
                        { onNavigate(Screen.Search.route) }, iconOnly = true) {
                        Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp))
                    }
                    CinemaNavButton("Home", currentRoute == Screen.Home.route && filter == null,
                        { filter = null; onNavigate(Screen.Home.route) }, Modifier.focusRequester(homeRequester))
                    CinemaNavButton("Shows", currentRoute == Screen.Home.route && filter == "series",
                        { filter = "series"; onNavigate(Screen.Home.route) })
                    CinemaNavButton("Movies", currentRoute == Screen.Home.route && filter == "movie",
                        { filter = "movie"; onNavigate(Screen.Home.route) })
                    if (Screen.Discover.route in rootRoutes) CinemaNavButton("Browse", currentRoute == Screen.Discover.route,
                        { onNavigate(Screen.Discover.route) })
                    CinemaNavButton("My List", currentRoute == Screen.Library.route,
                        { onNavigate(Screen.Library.route) })
                    CinemaNavButton("Settings", currentRoute == Screen.Settings.route,
                        { onNavigate(Screen.Settings.route) })
                    Spacer(Modifier.weight(1f))
                    Image(painterResource(R.drawable.cinema_wordmark), "Netflix",
                        Modifier.width(88.dp).height(30.dp), contentScale = ContentScale.Fit)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().focusRequester(contentRequester)) { content() }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CinemaNavButton(
    label: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, iconOnly: Boolean = false, leading: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick, modifier = modifier,
        shape = ButtonDefaults.shape(RoundedCornerShape(22.dp)),
        scale = ButtonDefaults.scale(focusedScale = 1.04f),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) Color(0xFF2B2B2B) else Color.Transparent,
            contentColor = if (selected) Color.White else Color(0xFFBEBEBE),
            focusedContainerColor = Color.White, focusedContentColor = Color.Black
        ),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leading?.invoke()
            if (!iconOnly) Text(label, fontSize = 13.sp)
        }
    }
}
