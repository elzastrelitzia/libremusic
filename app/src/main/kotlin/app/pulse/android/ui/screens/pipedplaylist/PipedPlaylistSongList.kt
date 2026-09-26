package app.pulse.android.ui.screens.pipedplaylist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pulse.android.LocalPlayerAwareWindowInsets
import app.pulse.android.LocalPlayerServiceBinder
import app.pulse.android.R
import app.pulse.android.ui.components.LocalMenuState
import app.pulse.android.ui.components.ShimmerHost
import app.pulse.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.pulse.android.ui.components.themed.Header
import app.pulse.android.ui.components.themed.HeaderPlaceholder
import app.pulse.android.ui.components.themed.LayoutWithAdaptiveThumbnail
import app.pulse.android.ui.components.themed.NonQueuedMediaItemMenu
import app.pulse.android.ui.components.themed.SecondaryTextButton
import app.pulse.android.ui.components.themed.adaptiveThumbnailContent
import app.pulse.android.ui.items.SongItem
import app.pulse.android.ui.items.SongItemPlaceholder
import app.pulse.android.utils.PlaylistDownloadIcon
import app.pulse.android.utils.asMediaItem
import app.pulse.android.utils.enqueue
import app.pulse.android.utils.forcePlayAtIndex
import app.pulse.android.utils.forcePlayFromBeginning
import app.pulse.android.utils.playingSong
import app.pulse.compose.persist.persist
import app.pulse.core.ui.Dimensions
import app.pulse.core.ui.LocalAppearance
import app.pulse.core.ui.utils.isLandscape
import app.pulse.providers.piped.Piped
import app.pulse.providers.piped.models.Playlist
import app.pulse.providers.piped.models.Session
import com.valentinilk.shimmer.shimmer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PipedPlaylistSongList(
    session: Session,
    playlistId: UUID,
    modifier: Modifier = Modifier
) {
    val (colorPalette) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current

    var playlist by persist<Playlist>(tag = "pipedplaylist/$playlistId/playlistPage")
    val mediaItems = remember(playlist) {
        playlist?.videos?.mapNotNull { it.asMediaItem }?.toImmutableList()
    }

    LaunchedEffect(Unit) {
        playlist = withContext(Dispatchers.IO) {
            Piped.playlist.songs(
                session = session,
                id = playlistId
            )?.getOrNull()
        }
    }

    val lazyListState = rememberLazyListState()

    val thumbnailContent = adaptiveThumbnailContent(
        isLoading = playlist == null,
        url = playlist?.thumbnailUrl?.toString()
    )

    val (currentMediaId, playing) = playingSong(binder)

    LayoutWithAdaptiveThumbnail(
        thumbnailContent = thumbnailContent,
        modifier = modifier
    ) {
        Box {
            LazyColumn(
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
                modifier = Modifier
                    .background(colorPalette.background0)
                    .fillMaxSize()
            ) {
                item(
                    key = "header",
                    contentType = 0
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (playlist == null) HeaderPlaceholder(modifier = Modifier.shimmer())
                        else Header(title = playlist?.name ?: stringResource(R.string.unknown)) {
                            SecondaryTextButton(
                                text = stringResource(R.string.enqueue),
                                enabled = playlist?.videos?.isNotEmpty() == true,
                                onClick = {
                                    mediaItems?.let { binder?.player?.enqueue(it) }
                                }
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            mediaItems?.let { PlaylistDownloadIcon(it) }
                        }

                        if (!isLandscape) thumbnailContent()
                    }
                }

                itemsIndexed(items = playlist?.videos ?: emptyList()) { index, song ->
                    song.asMediaItem?.let { mediaItem ->
                        SongItem(
                            song = mediaItem,
                            thumbnailSize = Dimensions.thumbnails.song,
                            modifier = Modifier.combinedClickable(
                                onLongClick = {
                                    menuState.display {
                                        NonQueuedMediaItemMenu(
                                            onDismiss = menuState::hide,
                                            mediaItem = mediaItem
                                        )
                                    }
                                },
                                onClick = {
                                    playlist?.videos?.mapNotNull(Playlist.Video::asMediaItem)
                                        ?.let { mediaItems ->
                                            binder?.stopRadio()
                                            binder?.player?.forcePlayAtIndex(mediaItems, index)
                                        }
                                }
                            ),
                            isPlaying = playing && currentMediaId == song.id
                        )
                    }
                }

                if (playlist == null) item(key = "loading") {
                    ShimmerHost(modifier = Modifier.fillParentMaxSize()) {
                        repeat(4) {
                            SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                        }
                    }
                }
            }

            FloatingActionsContainerWithScrollToTop(
                lazyListState = lazyListState,
                icon = R.drawable.shuffle,
                onClick = {
                    playlist?.videos?.let { songs ->
                        if (songs.isNotEmpty()) {
                            binder?.stopRadio()
                            binder?.player?.forcePlayFromBeginning(
                                songs.shuffled().mapNotNull(Playlist.Video::asMediaItem)
                            )
                        }
                    }
                }
            )
        }
    }
}
