package app.pulse.android.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pulse.android.R
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

@Composable
fun LibraryScreen() {
    RouteHandler {
        GlobalRoutes()

        songsRoute { SongsScreen() }
        playlistsRoute { PlaylistsScreen() }
        artistsRoute { ArtistsScreen() }
        albumsRoute { AlbumsScreen() }
        localSongsRoute { LocalSongsScreen() }

        Content {
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
                        description = stringResource(R.string.songs),
                        icon = R.drawable.musical_notes,
                        onClick = { songsRoute() }
                    )
                    SettingsMenuEntry(
                        title = stringResource(R.string.playlists),
                        description = stringResource(R.string.playlists),
                        icon = R.drawable.playlist,
                        onClick = { playlistsRoute() }
                    )
                    SettingsMenuEntry(
                        title = stringResource(R.string.artists),
                        description = stringResource(R.string.artists),
                        icon = R.drawable.person,
                        onClick = { artistsRoute() }
                    )
                    SettingsMenuEntry(
                        title = stringResource(R.string.albums),
                        description = stringResource(R.string.albums),
                        icon = R.drawable.disc,
                        onClick = { albumsRoute() }
                    )
                    SettingsMenuEntry(
                        title = stringResource(R.string.local),
                        description = stringResource(R.string.local),
                        icon = R.drawable.download,
                        onClick = { localSongsRoute() }
                    )
                }
            }
        }
    }
}
