package app.pulse.android.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import app.pulse.android.Database
import app.pulse.android.LocalPlayerServiceBinder
import app.pulse.android.R
import app.pulse.android.handleUrl
import app.pulse.android.models.Mood
import app.pulse.android.models.SearchQuery
import app.pulse.android.preferences.DataPreferences
import app.pulse.android.query
import app.pulse.android.ui.screens.album.AlbumScreen
import app.pulse.android.ui.screens.artist.ArtistScreen
import app.pulse.android.ui.screens.pipedplaylist.PipedPlaylistScreen
import app.pulse.android.ui.screens.playlist.PlaylistScreen
import app.pulse.android.ui.screens.search.SearchScreen
import app.pulse.android.ui.screens.searchresult.SearchResultScreen
import app.pulse.android.ui.screens.mood.MoodScreen
import app.pulse.android.ui.screens.settings.About
import app.pulse.android.ui.screens.settings.AppearanceSettings
import app.pulse.android.ui.screens.settings.CacheSettings
import app.pulse.android.ui.screens.settings.DatabaseSettings
import app.pulse.android.ui.screens.settings.LogsScreen
import app.pulse.android.ui.screens.settings.OtherSettings
import app.pulse.android.ui.screens.settings.PlayerSettings
import app.pulse.android.ui.screens.settings.SettingsScreen
import app.pulse.android.ui.screens.settings.SyncSettings
import app.pulse.android.utils.toast
import app.pulse.compose.routing.Route0
import app.pulse.compose.routing.Route1
import app.pulse.compose.routing.Route3
import app.pulse.compose.routing.Route4
import app.pulse.compose.routing.RouteHandlerScope
import app.pulse.compose.routing.RouteTransition
import app.pulse.core.data.enums.BuiltInPlaylist
import io.ktor.http.Url
import java.util.UUID

/**
 * Marker class for linters that a composable is a route and should not be handled like a regular
 * composable, but rather as an entrypoint.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class Route

val albumRoute = Route1<String>("albumRoute")
val artistRoute = Route1<String>("artistRoute")
val builtInPlaylistRoute = Route1<BuiltInPlaylist>("builtInPlaylistRoute")
val localPlaylistRoute = Route1<Long>("localPlaylistRoute")
val logsRoute = Route0("logsRoute")
val pipedPlaylistRoute = Route3<String, String, String>("pipedPlaylistRoute")
val playlistRoute = Route4<String, String?, Int?, Boolean>("playlistRoute")
val moodRoute = Route1<Mood>("moodRoute")
val searchResultRoute = Route1<String>("searchResultRoute", RouteTransition.Fade)
val searchRoute = Route1<String>("searchRoute", RouteTransition.Fade)
val settingsRoute = Route0("settingsRoute")
val appearanceSettingsRoute = Route0("appearanceSettingsRoute")
val playerSettingsRoute = Route0("playerSettingsRoute")
val cacheSettingsRoute = Route0("cacheSettingsRoute")
val databaseSettingsRoute = Route0("databaseSettingsRoute")
val syncSettingsRoute = Route0("syncSettingsRoute")
val otherSettingsRoute = Route0("otherSettingsRoute")
val aboutSettingsRoute = Route0("aboutSettingsRoute")

@Composable
fun RouteHandlerScope.GlobalRoutes() {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current

    albumRoute { browseId ->
        AlbumScreen(browseId = browseId)
    }

    artistRoute { browseId ->
        ArtistScreen(browseId = browseId)
    }

    logsRoute {
        LogsScreen()
    }

    pipedPlaylistRoute { apiBaseUrl, sessionToken, playlistId ->
        PipedPlaylistScreen(
            apiBaseUrl = runCatching { Url(apiBaseUrl) }.getOrNull()
                ?: error("Invalid apiBaseUrl: $apiBaseUrl is not a valid Url"),
            sessionToken = sessionToken,
            playlistId = runCatching {
                UUID.fromString(playlistId)
            }.getOrNull() ?: error("Invalid playlistId: $playlistId is not a valid UUID")
        )
    }

    playlistRoute { browseId, params, maxDepth, shouldDedup ->
        PlaylistScreen(
            browseId = browseId,
            params = params,
            maxDepth = maxDepth,
            shouldDedup = shouldDedup
        )
    }

    moodRoute { mood ->
        MoodScreen(mood = mood)
    }

    settingsRoute {
        SettingsScreen()
    }

    appearanceSettingsRoute {
        AppearanceSettings()
    }

    playerSettingsRoute {
        PlayerSettings()
    }

    cacheSettingsRoute {
        CacheSettings()
    }

    databaseSettingsRoute {
        DatabaseSettings()
    }

    syncSettingsRoute {
        SyncSettings()
    }

    otherSettingsRoute {
        OtherSettings()
    }

    aboutSettingsRoute {
        About()
    }

    searchRoute { initialTextInput ->
        SearchScreen(
            initialTextInput = initialTextInput,
            onSearch = { query ->
                searchResultRoute(query)

                if (!DataPreferences.pauseSearchHistory) query {
                    Database.insert(SearchQuery(query = query))
                }
            },
            onViewPlaylist = { url ->
                with(context) {
                    runCatching {
                        handleUrl(url.toUri(), binder)
                    }.onFailure {
                        toast(getString(R.string.error_url, url))
                    }
                }
            }
        )
    }

    searchResultRoute { query ->
        SearchResultScreen(
            query = query,
            onSearchAgain = { searchRoute(query) }
        )
    }
}
