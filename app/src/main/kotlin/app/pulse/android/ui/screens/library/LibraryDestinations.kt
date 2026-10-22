package app.pulse.android.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import app.pulse.android.ui.components.themed.LocalDockBackArrow
import app.pulse.android.ui.screens.GlobalRoutes
import app.pulse.android.ui.screens.Route
import app.pulse.android.ui.screens.albumRoute
import app.pulse.android.ui.screens.artistRoute
import app.pulse.android.ui.screens.home.HomeAlbums
import app.pulse.android.ui.screens.home.HomeArtistList
import app.pulse.android.ui.screens.home.HomeLocalSongs
import app.pulse.android.ui.screens.home.HomePlaylists
import app.pulse.android.ui.screens.home.HomeSongs
import app.pulse.android.ui.screens.localPlaylistRoute
import app.pulse.android.ui.screens.localplaylist.LocalPlaylistScreen
import app.pulse.android.ui.screens.pipedPlaylistRoute
import app.pulse.android.ui.screens.searchRoute
import app.pulse.compose.routing.RouteHandler

// These are pushed screens, so HomeScreen's Scaffold is disposed and the dock sees a null
// NavigationState. That already makes it morph to its compact sub-page shape, so these
// screens deliberately do not hide it. Each `Home*` composable draws its own collapsing
// header and applies its own window insets, which clear the compact dock.

/** Shows the dock's back arrow while this pushed screen is on top. */
@Composable
private fun DockBackArrow() {
    val backArrow = LocalDockBackArrow.current
    DisposableEffect(Unit) {
        backArrow.value++
        onDispose { backArrow.value-- }
    }
}

/**
 * A route handler has to own the next push. HomeScreen's handler stops listening once one
 * of these screens is its child, so without this wrapper everything these screens push
 * (a local playlist, search, an artist) had no listener and was dropped silently.
 * Only this handler is listening here, so pushes stay single.
 */
@Composable
private fun LibraryDestination(content: @Composable () -> Unit) {
    RouteHandler {
        GlobalRoutes()

        localPlaylistRoute { playlistId ->
            LocalPlaylistScreen(playlistId = playlistId)
        }

        Content {
            DockBackArrow()
            content()
        }
    }
}

@Route
@Composable
fun SongsScreen() = LibraryDestination {
    HomeSongs(onSearchClick = { searchRoute.global("") })
}

@Route
@Composable
fun PlaylistsScreen() = LibraryDestination {
    HomePlaylists(
        onPlaylistClick = { localPlaylistRoute.global(it.id) },
        onPipedPlaylistClick = { session, playlist ->
            pipedPlaylistRoute.global(
                session.apiBaseUrl.toString(),
                session.token,
                playlist.id.toString()
            )
        },
        onSearchClick = { searchRoute.global("") }
    )
}

@Route
@Composable
fun ArtistsScreen() = LibraryDestination {
    HomeArtistList(
        onArtistClick = { artistRoute.global(it.id) },
        onSearchClick = { searchRoute.global("") }
    )
}

@Route
@Composable
fun AlbumsScreen() = LibraryDestination {
    HomeAlbums(
        onAlbumClick = { albumRoute.global(it.id) },
        onSearchClick = { searchRoute.global("") }
    )
}

@Route
@Composable
fun LocalSongsScreen() = LibraryDestination {
    HomeLocalSongs(onSearchClick = { searchRoute.global("") })
}
