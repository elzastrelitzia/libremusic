package app.pulse.android.ui.screens.searchresult

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pulse.android.LocalPlayerAwareWindowInsets
import app.pulse.android.LocalPlayerServiceBinder
import app.pulse.android.R
import app.pulse.android.preferences.UIStatePreferences
import app.pulse.android.ui.components.LocalMenuState
import app.pulse.android.ui.components.themed.NonQueuedMediaItemMenu
import app.pulse.android.ui.components.themed.Scaffold
import app.pulse.android.ui.items.AlbumItem
import app.pulse.android.ui.items.AlbumItemPlaceholder
import app.pulse.android.ui.items.ArtistItem
import app.pulse.android.ui.items.ArtistItemPlaceholder
import app.pulse.android.ui.items.PlaylistItem
import app.pulse.android.ui.items.PlaylistItemPlaceholder
import app.pulse.android.ui.items.SongItem
import app.pulse.android.ui.items.SongItemPlaceholder
import app.pulse.android.ui.items.VideoItem
import app.pulse.android.ui.items.VideoItemPlaceholder
import app.pulse.android.ui.screens.GlobalRoutes
import app.pulse.android.ui.screens.Route
import app.pulse.android.ui.screens.albumRoute
import app.pulse.android.ui.screens.artistRoute
import app.pulse.android.ui.screens.playlistRoute
import app.pulse.android.utils.asMediaItem
import app.pulse.android.utils.forcePlay
import app.pulse.android.utils.medium
import app.pulse.android.utils.playingSong
import app.pulse.compose.persist.LocalPersistMap
import app.pulse.compose.persist.PersistMapCleanup
import app.pulse.compose.routing.RouteHandler
import app.pulse.core.ui.Dimensions
import app.pulse.core.ui.LocalAppearance
import app.pulse.providers.innertube.Innertube
import app.pulse.providers.innertube.models.bodies.ContinuationBody
import app.pulse.providers.innertube.models.bodies.SearchBody
import app.pulse.providers.innertube.requests.searchPage
import app.pulse.providers.innertube.utils.from

@OptIn(ExperimentalFoundationApi::class)
@Route
@Composable
fun SearchResultScreen(query: String, onSearchAgain: () -> Unit) {
    val persistMap = LocalPersistMap.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current

    val saveableStateHolder = rememberSaveableStateHolder()

    PersistMapCleanup(prefix = "searchResults/$query/")

    val (currentMediaId, playing) = playingSong(binder)

    RouteHandler {
        GlobalRoutes()

        Content {
            val (colorPalette) = LocalAppearance.current

            val headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit = {
                val (colorPalette, typography) = LocalAppearance.current
                val pillShape = RoundedCornerShape(percent = 50)

                // no Header wrapper, its 16.dp top padding was the dead gap above the pill
                // make it tuff and clean
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                        .fillMaxWidth()
                        .clip(pillShape)
                        .background(colorPalette.background1)
                        .border(
                            width = 0.5.dp,
                            color = colorPalette.textSecondary.copy(alpha = 0.35f),
                            shape = pillShape
                        )
                        .pointerInput(Unit) {
                            detectTapGestures {
                                persistMap?.clean("searchResults/$query/")
                                onSearchAgain()
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    BasicText(
                        text = query,
                        style = typography.l.medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val backdropContent: @Composable () -> Unit = {
                val topInset = LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Top)
                    .asPaddingValues()
                    .calculateTopPadding()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // ends just below the pill (inset + 52 + 16 tail) so
                        // first results stay visible at rest; fades items as
                        // they scroll under pill/status bar
                        .height(68.dp + topInset)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    colorPalette.background0,
                                    colorPalette.background0.copy(alpha = 0.8f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Scaffold(
                key = "searchresult",
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = UIStatePreferences.searchResultScreenTabIndex,
                onTabChange = { UIStatePreferences.searchResultScreenTabIndex = it },
                tabColumnContent = {
                    tab(0, R.string.songs, R.drawable.musical_notes)
                    tab(1, R.string.albums, R.drawable.disc)
                    tab(2, R.string.artists, R.drawable.person)
                    tab(3, R.string.videos, R.drawable.film)
                    tab(4, R.string.playlists, R.drawable.playlist)
                }
            ) { tabIndex ->
                saveableStateHolder.SaveableStateProvider(tabIndex) {
                    when (tabIndex) {
                        0 -> ItemsPage(
                            tag = "searchResults/$query/songs",
                            provider = { continuation ->
                                if (continuation == null) Innertube.searchPage(
                                    body = SearchBody(
                                        query = query,
                                        params = Innertube.SearchFilter.Song.value
                                    ),
                                    fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
                                ) else Innertube.searchPage(
                                    body = ContinuationBody(continuation = continuation),
                                    fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
                                )
                            },
                            emptyItemsText = stringResource(R.string.no_search_results),
                            header = headerContent,
                            stickyHeader = true,
                            backdrop = backdropContent,
                            itemContent = { song ->
                                SongItem(
                                    song = song,
                                    thumbnailSize = Dimensions.thumbnails.song,
                                    modifier = Modifier.combinedClickable(
                                        onLongClick = {
                                            menuState.display {
                                                NonQueuedMediaItemMenu(
                                                    onDismiss = menuState::hide,
                                                    mediaItem = song.asMediaItem
                                                )
                                            }
                                        },
                                        onClick = {
                                            binder?.stopRadio()
                                            binder?.player?.forcePlay(song.asMediaItem)
                                            binder?.setupRadio(song.info?.endpoint)
                                        }
                                    ),
                                    isPlaying = playing && currentMediaId == song.key
                                )
                            },
                            itemPlaceholderContent = {
                                SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                            }
                        )

                        1 -> ItemsPage(
                            tag = "searchResults/$query/albums",
                            provider = { continuation ->
                                if (continuation == null) {
                                    Innertube.searchPage(
                                        body = SearchBody(
                                            query = query,
                                            params = Innertube.SearchFilter.Album.value
                                        ),
                                        fromMusicShelfRendererContent = Innertube.AlbumItem::from
                                    )
                                } else {
                                    Innertube.searchPage(
                                        body = ContinuationBody(continuation = continuation),
                                        fromMusicShelfRendererContent = Innertube.AlbumItem::from
                                    )
                                }
                            },
                            emptyItemsText = stringResource(R.string.no_search_results),
                            header = headerContent,
                            stickyHeader = true,
                            backdrop = backdropContent,
                            itemContent = { album ->
                                AlbumItem(
                                    album = album,
                                    thumbnailSize = Dimensions.thumbnails.album,
                                    modifier = Modifier.clickable(onClick = { albumRoute(album.key) })
                                )
                            },
                            itemPlaceholderContent = {
                                AlbumItemPlaceholder(thumbnailSize = Dimensions.thumbnails.album)
                            }
                        )

                        2 -> ItemsPage(
                            tag = "searchResults/$query/artists",
                            provider = { continuation ->
                                if (continuation == null) {
                                    Innertube.searchPage(
                                        body = SearchBody(
                                            query = query,
                                            params = Innertube.SearchFilter.Artist.value
                                        ),
                                        fromMusicShelfRendererContent = Innertube.ArtistItem::from
                                    )
                                } else {
                                    Innertube.searchPage(
                                        body = ContinuationBody(continuation = continuation),
                                        fromMusicShelfRendererContent = Innertube.ArtistItem::from
                                    )
                                }
                            },
                            emptyItemsText = stringResource(R.string.no_search_results),
                            header = headerContent,
                            stickyHeader = true,
                            backdrop = backdropContent,
                            itemContent = { artist ->
                                ArtistItem(
                                    artist = artist,
                                    thumbnailSize = 64.dp,
                                    modifier = Modifier
                                        .clickable(onClick = { artistRoute(artist.key) })
                                )
                            },
                            itemPlaceholderContent = {
                                ArtistItemPlaceholder(thumbnailSize = 64.dp)
                            }
                        )

                        3 -> ItemsPage(
                            tag = "searchResults/$query/videos",
                            provider = { continuation ->
                                if (continuation == null) Innertube.searchPage(
                                    body = SearchBody(
                                        query = query,
                                        params = Innertube.SearchFilter.Video.value
                                    ),
                                    fromMusicShelfRendererContent = Innertube.VideoItem::from
                                ) else Innertube.searchPage(
                                    body = ContinuationBody(continuation = continuation),
                                    fromMusicShelfRendererContent = Innertube.VideoItem::from
                                )
                            },
                            emptyItemsText = stringResource(R.string.no_search_results),
                            header = headerContent,
                            stickyHeader = true,
                            backdrop = backdropContent,
                            itemContent = { video ->
                                VideoItem(
                                    video = video,
                                    thumbnailWidth = 128.dp,
                                    thumbnailHeight = 72.dp,
                                    modifier = Modifier.combinedClickable(
                                        onLongClick = {
                                            menuState.display {
                                                NonQueuedMediaItemMenu(
                                                    mediaItem = video.asMediaItem,
                                                    onDismiss = menuState::hide
                                                )
                                            }
                                        },
                                        onClick = {
                                            binder?.stopRadio()
                                            binder?.player?.forcePlay(video.asMediaItem)
                                            binder?.setupRadio(video.info?.endpoint)
                                        }
                                    )
                                )
                            },
                            itemPlaceholderContent = {
                                VideoItemPlaceholder(
                                    thumbnailWidth = 128.dp,
                                    thumbnailHeight = 72.dp
                                )
                            }
                        )

                        4 -> ItemsPage(
                            tag = "searchResults/$query/playlists",
                            provider = { continuation ->
                                if (continuation == null) Innertube.searchPage(
                                    body = SearchBody(
                                        query = query,
                                        params = Innertube.SearchFilter.CommunityPlaylist.value
                                    ),
                                    fromMusicShelfRendererContent = Innertube.PlaylistItem::from
                                ) else Innertube.searchPage(
                                    body = ContinuationBody(continuation = continuation),
                                    fromMusicShelfRendererContent = Innertube.PlaylistItem::from
                                )
                            },
                            emptyItemsText = stringResource(R.string.no_search_results),
                            header = headerContent,
                            stickyHeader = true,
                            backdrop = backdropContent,
                            itemContent = { playlist ->
                                PlaylistItem(
                                    playlist = playlist,
                                    thumbnailSize = Dimensions.thumbnails.playlist,
                                    modifier = Modifier.clickable {
                                        playlistRoute(playlist.key, null, null, false)
                                    }
                                )
                            },
                            itemPlaceholderContent = {
                                PlaylistItemPlaceholder(thumbnailSize = Dimensions.thumbnails.playlist)
                            }
                        )
                    }
                }
            }
        }
    }
}
