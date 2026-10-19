package app.pulse.android.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pulse.android.Database
import app.pulse.android.R
import app.pulse.android.preferences.OrderPreferences
import app.pulse.android.ui.screens.GlobalRoutes
import app.pulse.android.ui.screens.albumsRoute
import app.pulse.android.ui.screens.artistsRoute
import app.pulse.android.ui.screens.localSongsRoute
import app.pulse.android.ui.screens.playlistsRoute
import app.pulse.android.ui.screens.settings.MasterSettingsCategoryScreen
import app.pulse.android.ui.screens.settings.SettingsMenuEntry
import app.pulse.android.ui.screens.songsRoute
import app.pulse.compose.routing.RouteHandler
import app.pulse.core.ui.LocalAppearance
import kotlinx.coroutines.flow.first

@Composable
fun LibraryScreen() = with(OrderPreferences) {
    RouteHandler {
        GlobalRoutes()

        songsRoute { SongsScreen() }
        playlistsRoute { PlaylistsScreen() }
        artistsRoute { ArtistsScreen() }
        albumsRoute { AlbumsScreen() }
        localSongsRoute { LocalSongsScreen() }

        Content {
            var songsCount by remember { mutableIntStateOf(0) }
            var playlistsCount by remember { mutableIntStateOf(0) }
            var artistsCount by remember { mutableIntStateOf(0) }
            var albumsCount by remember { mutableIntStateOf(0) }
            var localSongsCount by remember { mutableIntStateOf(0) }

            // Read once on open rather than keeping five ongoing collectors alive, this
            // menu is not the screen the user is editing the library from, and a count
            // line that lags until the page is reopened is enough. ponytail: swap to
            // .collect { } per row if live counts ever matter.
            LaunchedEffect(Unit) {
                songsCount = Database.songs(songSortBy, songSortOrder).first()
                    .count { it.totalPlayTimeMs > 0L }
                playlistsCount = Database.playlistPreviews(playlistSortBy, playlistSortOrder).first().size
                artistsCount = Database.artists(artistSortBy, artistSortOrder).first().size
                albumsCount = Database.albums(albumSortBy, albumSortOrder).first().size
                localSongsCount = Database.songs(songSortBy, songSortOrder, isLocal = true).first().size
            }

            MasterSettingsCategoryScreen(
                title = stringResource(R.string.library)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(LocalAppearance.current.colorPalette.background1)
                ) {
                    SettingsMenuEntry(
                        title = stringResource(R.string.songs),
                        description = pluralStringResource(R.plurals.n_songs, songsCount, songsCount),
                        icon = R.drawable.musical_notes,
                        onClick = { songsRoute() }
                    )
                    SettingsMenuEntry(
                        title = stringResource(R.string.playlists),
                        description = pluralStringResource(R.plurals.n_playlists, playlistsCount, playlistsCount),
                        icon = R.drawable.playlist,
                        onClick = { playlistsRoute() }
                    )
                    SettingsMenuEntry(
                        title = stringResource(R.string.artists),
                        description = pluralStringResource(R.plurals.n_artists, artistsCount, artistsCount),
                        icon = R.drawable.person,
                        onClick = { artistsRoute() }
                    )
                    SettingsMenuEntry(
                        title = stringResource(R.string.albums),
                        description = pluralStringResource(R.plurals.n_albums, albumsCount, albumsCount),
                        icon = R.drawable.disc,
                        onClick = { albumsRoute() }
                    )
                    SettingsMenuEntry(
                        title = stringResource(R.string.local),
                        description = pluralStringResource(R.plurals.n_local_songs, localSongsCount, localSongsCount),
                        icon = R.drawable.download,
                        onClick = { localSongsRoute() }
                    )
                }
            }
        }
    }
}
