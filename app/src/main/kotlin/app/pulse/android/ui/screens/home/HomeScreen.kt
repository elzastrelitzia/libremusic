package app.pulse.android.ui.screens.home

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import app.pulse.android.R
import app.pulse.android.models.toUiMood
import app.pulse.android.preferences.UIStatePreferences
import app.pulse.android.ui.components.themed.Scaffold
import app.pulse.android.ui.screens.GlobalRoutes
import app.pulse.android.ui.screens.Route
import app.pulse.android.ui.screens.SETTINGS_ACTION_ID
import app.pulse.android.ui.screens.albumRoute
import app.pulse.android.ui.screens.albumsRoute
import app.pulse.android.ui.screens.artistRoute
import app.pulse.android.ui.screens.artistsRoute
import app.pulse.android.ui.screens.builtInPlaylistRoute
import app.pulse.android.ui.screens.builtinplaylist.BuiltInPlaylistScreen
import app.pulse.android.ui.screens.library.LibraryScreen
import app.pulse.android.ui.screens.library.AlbumsScreen
import app.pulse.android.ui.screens.library.ArtistsScreen
import app.pulse.android.ui.screens.library.LocalSongsScreen
import app.pulse.android.ui.screens.library.PlaylistsScreen
import app.pulse.android.ui.screens.library.SongsScreen
import app.pulse.android.ui.screens.localPlaylistRoute
import app.pulse.android.ui.screens.localSongsRoute
import app.pulse.android.ui.screens.playlistsRoute
import app.pulse.android.ui.screens.songsRoute
import app.pulse.android.ui.screens.localplaylist.LocalPlaylistScreen
import app.pulse.android.ui.screens.mood.MoodScreen
import app.pulse.android.ui.screens.mood.MoreAlbumsScreen
import app.pulse.android.ui.screens.mood.MoreMoodsScreen
import app.pulse.android.ui.screens.moodRoute
import app.pulse.android.ui.screens.playlistRoute
import app.pulse.android.ui.screens.searchRoute
import app.pulse.android.ui.screens.settingsRoute
import app.pulse.compose.persist.PersistMapCleanup
import app.pulse.compose.routing.Route0
import app.pulse.compose.routing.RouteHandler

private val moreMoodsRoute = Route0("moreMoodsRoute")
private val moreAlbumsRoute = Route0("moreAlbumsRoute")

@Route
@Composable
fun HomeScreen() {
    val saveableStateHolder = rememberSaveableStateHolder()

    PersistMapCleanup("home/")

    RouteHandler {
        GlobalRoutes()

        localPlaylistRoute { playlistId ->
            LocalPlaylistScreen(playlistId = playlistId)
        }

        builtInPlaylistRoute { builtInPlaylist ->
            BuiltInPlaylistScreen(builtInPlaylist = builtInPlaylist)
        }

        songsRoute { SongsScreen() }

        playlistsRoute { PlaylistsScreen() }

        artistsRoute { ArtistsScreen() }

        albumsRoute { AlbumsScreen() }

        localSongsRoute { LocalSongsScreen() }

        moodRoute { mood ->
            MoodScreen(mood = mood)
        }

        moreMoodsRoute {
            MoreMoodsScreen()
        }

        moreAlbumsRoute {
            MoreAlbumsScreen()
        }

        Content {
            // Indices 3..6 were library destinations before the Library tab existed.
            // Remap once, then remember it was done, so the stored value stops being
            // wrong rather than being masked on every read. The read-side clamp is the
            // backstop; this repairs the preference.
            if (!UIStatePreferences.homeTabsMigrated) {
                if (UIStatePreferences.homeScreenTabIndex > 2) UIStatePreferences.homeScreenTabIndex = 2
                UIStatePreferences.homeTabsMigrated = true
            }

            // Count of entries with no actionId - an action entry is never a valid selected
            // index. Written by hand because tabColumnContent's builder is only reachable
            // inside Scaffold, and the migration above already hard-codes this range.
            // The two change together if a real tab is ever added.
            val selectableTabCount = 3
            val clampedTabIndex = UIStatePreferences.homeScreenTabIndex.coerceIn(0, selectableTabCount - 1)
            if (clampedTabIndex != UIStatePreferences.homeScreenTabIndex) {
                Log.w("HomeScreen", "clamped homeScreenTabIndex ${UIStatePreferences.homeScreenTabIndex} -> $clampedTabIndex")
            }

            Scaffold(
                key = "home",
                // Landscape-only, the rail draws this gear, and the dock (with its own Settings
                // entry) is not drawn in landscape. Passing 0 here would lose the only Settings
                // affordance in that orientation.
                topIconButtonId = R.drawable.settings,
                onTopIconButtonClick = { settingsRoute.global() },
                isGlobalNav = true,
                tabIndex = clampedTabIndex,
                onTabChange = { UIStatePreferences.homeScreenTabIndex = it },
                onAction = { id ->
                    when (id) {
                        SETTINGS_ACTION_ID -> settingsRoute.global()
                        else -> Unit   // unknown id no-op, never crash
                    }
                },
                tabColumnContent = {
                    tab(0, R.string.home, R.drawable.sparkles)
                    tab(1, R.string.discover, R.drawable.globe)
                    tab(2, R.string.library, R.drawable.library, canHide = false)
                    tab("settings", R.string.settings, R.drawable.settings, canHide = false, actionId = SETTINGS_ACTION_ID)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    val onSearchClick = { searchRoute("") }
                    when (currentTabIndex) {
                        0 -> QuickPicks(
                            onAlbumClick = { albumRoute(it.key) },
                            onArtistClick = { artistRoute(it.key) },
                            onPlaylistClick = {
                                playlistRoute(
                                    p0 = it.key,
                                    p1 = null,
                                    p2 = null,
                                    p3 = it.channel?.name == "YouTube Music"
                                )
                            }
                        )

                        1 -> HomeDiscovery(
                            onMoodClick = { mood -> moodRoute(mood.toUiMood()) },
                            onNewReleaseAlbumClick = { albumRoute(it) },
                            onSearchClick = onSearchClick,
                            onMoreMoodsClick = { moreMoodsRoute() },
                            onMoreAlbumsClick = { moreAlbumsRoute() },
                            onPlaylistClick = { playlistRoute(it, null, null, true) }
                        )

                        2 -> LibraryScreen()
                    }
                }
            }
        }
    }
}
