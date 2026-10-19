package app.pulse.android.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import app.pulse.android.R
import app.pulse.android.models.toUiMood
import app.pulse.android.preferences.UIStatePreferences
import app.pulse.android.ui.components.themed.Scaffold
import app.pulse.android.ui.screens.GlobalRoutes
import app.pulse.android.ui.screens.Route
import app.pulse.android.ui.screens.albumRoute
import app.pulse.android.ui.screens.artistRoute
import app.pulse.android.ui.screens.builtInPlaylistRoute
import app.pulse.android.ui.screens.builtinplaylist.BuiltInPlaylistScreen
import app.pulse.android.ui.screens.library.LibraryScreen
import app.pulse.android.ui.screens.localPlaylistRoute
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
            // Tabs 2..6 used to be library destinations. An install that had one
            // selected would otherwise land on a blank tab at an index this
            // scaffold no longer draws.
            if (UIStatePreferences.homeScreenTabIndex > 2) UIStatePreferences.homeScreenTabIndex = 2

            Scaffold(
                key = "home",
                topIconButtonId = R.drawable.settings,
                onTopIconButtonClick = { settingsRoute() },
                isGlobalNav = true,
                tabIndex = UIStatePreferences.homeScreenTabIndex,
                onTabChange = { UIStatePreferences.homeScreenTabIndex = it },
                tabColumnContent = {
                    tab(0, R.string.quick_picks, R.drawable.sparkles)
                    tab(1, R.string.discover, R.drawable.globe)
                    tab(2, R.string.library, R.drawable.library, canHide = false)
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
