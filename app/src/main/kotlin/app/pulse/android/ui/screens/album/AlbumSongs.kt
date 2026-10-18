package app.pulse.android.ui.screens.album

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.pulse.android.LocalPlayerAwareWindowInsets
import app.pulse.android.LocalPlayerServiceBinder
import app.pulse.core.data.models.Song
import app.pulse.android.ui.components.LocalMenuState
import app.pulse.android.ui.components.ShimmerHost
import app.pulse.android.ui.components.themed.CollapsingHeader
import app.pulse.android.ui.components.themed.NonQueuedMediaItemMenu
import app.pulse.android.ui.components.themed.PlaylistBackdrop
import app.pulse.android.ui.components.themed.PlaylistGradient
import app.pulse.android.ui.components.themed.PlaylistHero
import app.pulse.android.ui.items.SongItem
import app.pulse.android.ui.items.SongItemPlaceholder
import app.pulse.android.utils.asMediaItem
import app.pulse.android.utils.forcePlayAtIndex
import app.pulse.android.utils.forcePlayFromBeginning
import app.pulse.android.utils.playingSong
import app.pulse.core.ui.Dimensions
import kotlinx.collections.immutable.ImmutableList

// TODO: migrate to single impl for all 'song lists'
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumSongs(
    songs: ImmutableList<Song>,
    title: String?,
    mosaicUrls: List<String>,
    headerActions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    afterHeaderContent: (@Composable () -> Unit)? = null
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val lazyListState = rememberLazyListState()

    val (currentMediaId, playing) = playingSong(binder)

    val mediaItems = songs.map { it.asMediaItem }

    CollapsingHeader(
        title = title.orEmpty(),
        lazyListState = lazyListState,
        headerActions = headerActions
    ) {
    Box(modifier = modifier) {
        PlaylistBackdrop(mosaicUrls, lazyListState)
        PlaylistGradient()

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues(),
            modifier = Modifier.fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = 0
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PlaylistHero(
                        title = title.orEmpty(),
                        songCount = songs.size,
                        mosaicUrls = mosaicUrls,
                        onShuffle = {
                            binder?.stopRadio()
                            binder?.player?.forcePlayAtIndex(
                                items = mediaItems.shuffled(),
                                index = 0
                            )
                        },
                        onPlay = {
                            binder?.stopRadio()
                            binder?.player?.forcePlayFromBeginning(mediaItems)
                        },
                        onAdd = null,
                        shuffleWithLabel = true
                    )

                    afterHeaderContent?.invoke()
                }
            }

            itemsIndexed(
                items = songs,
                key = { _, song -> song.id }
            ) { index, song ->
                SongItem(
                    song = song,
                    index = index,
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
                            binder?.player?.forcePlayAtIndex(
                                items = mediaItems,
                                index = index
                            )
                        }
                    ),
                    isPlaying = playing && currentMediaId == song.id
                )
            }

            if (songs.isEmpty()) item(key = "loading") {
                ShimmerHost(modifier = Modifier.fillParentMaxSize()) {
                    repeat(4) {
                        SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                    }
                }
            }
        }
    }
    }
}
