package app.pulse.android.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import app.pulse.android.ui.components.themed.LocalDockHiddenCount
import app.pulse.android.ui.screens.Route
import app.pulse.android.ui.screens.albumRoute
import app.pulse.android.ui.screens.artistRoute
import app.pulse.android.ui.screens.home.HomeAlbums
import app.pulse.android.ui.screens.home.HomeArtistList
import app.pulse.android.ui.screens.home.HomeLocalSongs
import app.pulse.android.ui.screens.home.HomePlaylists
import app.pulse.android.ui.screens.home.HomeSongs
import app.pulse.android.ui.screens.localPlaylistRoute
import app.pulse.android.ui.screens.pipedPlaylistRoute
import app.pulse.android.ui.screens.searchRoute

/**
 * Each library destination is a pushed screen, so it hides the dock while it is
 * on screen. Each `Home*` composable already draws its own collapsing header and
 * applies its own window insets, so this adds nothing else.
 */
@Composable
private fun HideDock() {
    val dockHiddenCount = LocalDockHiddenCount.current
    DisposableEffect(Unit) {
        dockHiddenCount.value++
        onDispose { dockHiddenCount.value-- }
    }
}

@Route
@Composable
fun SongsScreen() {
    HideDock()
    HomeSongs(onSearchClick = { searchRoute.global("") })
}

@Route
@Composable
fun PlaylistsScreen() {
    HideDock()
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
fun ArtistsScreen() {
    HideDock()
    HomeArtistList(
        onArtistClick = { artistRoute.global(it.id) },
        onSearchClick = { searchRoute.global("") }
    )
}

@Route
@Composable
fun AlbumsScreen() {
    HideDock()
    HomeAlbums(
        onAlbumClick = { albumRoute.global(it.id) },
        onSearchClick = { searchRoute.global("") }
    )
}

@Route
@Composable
fun LocalSongsScreen() {
    HideDock()
    HomeLocalSongs(onSearchClick = { searchRoute.global("") })
}
