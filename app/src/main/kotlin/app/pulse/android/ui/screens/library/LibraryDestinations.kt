package app.pulse.android.ui.screens.library

import androidx.compose.runtime.Composable
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

// These are pushed screens, so HomeScreen's Scaffold is disposed and the dock sees a null
// NavigationState. That already makes it morph to its compact sub-page shape, so these
// screens deliberately do not hide it. Each `Home*` composable draws its own collapsing
// header and applies its own window insets, which clear the compact dock.

@Route
@Composable
fun SongsScreen() {
    HomeSongs(onSearchClick = { searchRoute.global("") })
}

@Route
@Composable
fun PlaylistsScreen() {
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
    HomeArtistList(
        onArtistClick = { artistRoute.global(it.id) },
        onSearchClick = { searchRoute.global("") }
    )
}

@Route
@Composable
fun AlbumsScreen() {
    HomeAlbums(
        onAlbumClick = { albumRoute.global(it.id) },
        onSearchClick = { searchRoute.global("") }
    )
}

@Route
@Composable
fun LocalSongsScreen() {
    HomeLocalSongs(onSearchClick = { searchRoute.global("") })
}
