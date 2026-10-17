package app.pulse.android.ui.screens.localplaylist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pulse.android.Database
import app.pulse.android.LocalPlayerAwareWindowInsets
import app.pulse.android.LocalPlayerServiceBinder
import app.pulse.android.R
import app.pulse.android.models.Playlist
import app.pulse.android.models.SongPlaylistMap
import app.pulse.android.preferences.DataPreferences
import app.pulse.android.query
import app.pulse.core.data.models.Song
import app.pulse.android.transaction
import app.pulse.android.ui.components.LocalMenuState
import app.pulse.android.ui.components.NewMenuEntry
import app.pulse.android.ui.components.themed.CollapsingHeader
import app.pulse.android.ui.components.themed.ConfirmationDialog
import app.pulse.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.pulse.android.ui.components.themed.InPlaylistMediaItemMenu
import app.pulse.android.ui.components.themed.PlaylistBackdrop
import app.pulse.android.ui.components.themed.PlaylistGradient
import app.pulse.android.ui.components.themed.PlaylistHeaderActions
import app.pulse.android.ui.components.themed.PlaylistHero
import app.pulse.android.ui.components.themed.ReorderHandle
import app.pulse.android.ui.components.themed.TextFieldDialog
import app.pulse.android.ui.items.SongItem
import app.pulse.android.utils.asMediaItem
import app.pulse.android.utils.completed
import app.pulse.android.utils.forcePlayAtIndex
import app.pulse.android.utils.forcePlayFromBeginning
import app.pulse.android.utils.launchYouTubeMusic
import app.pulse.android.utils.playingSong
import app.pulse.android.utils.toast
import app.pulse.compose.reordering.animateItemPlacement
import app.pulse.compose.reordering.draggedItem
import app.pulse.compose.reordering.rememberReorderingState
import app.pulse.core.ui.Dimensions
import app.pulse.core.ui.LocalAppearance
import app.pulse.providers.innertube.Innertube
import app.pulse.providers.innertube.models.bodies.BrowseBody
import app.pulse.providers.innertube.requests.playlistPage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalPlaylistSongs(
    playlist: Playlist,
    songs: ImmutableList<Song>,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorPalette = LocalAppearance.current.colorPalette
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    var loading by remember { mutableStateOf(false) }
    var isMenuVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (DataPreferences.autoSyncPlaylists) playlist.browseId?.let { browseId ->
            loading = true
            sync(playlist, browseId)
            loading = false
        }
    }

    val reorderingState = rememberReorderingState(
        lazyListState = lazyListState,
        key = songs,
        onDragEnd = { fromIndex, toIndex ->
            transaction {
                Database.move(playlist.id, fromIndex, toIndex)
            }
        },
        extraItemCount = 1
    )

    var isRenaming by rememberSaveable { mutableStateOf(false) }

    if (isRenaming) TextFieldDialog(
        hintText = stringResource(R.string.enter_playlist_name_prompt),
        initialTextInput = playlist.name,
        onDismiss = { isRenaming = false },
        onAccept = { text ->
            query {
                Database.update(playlist.copy(name = text))
            }
        }
    )

    var isDeleting by rememberSaveable { mutableStateOf(false) }

    if (isDeleting) ConfirmationDialog(
        text = stringResource(R.string.confirm_delete_playlist),
        onDismiss = { isDeleting = false },
        onConfirm = {
            query {
                Database.delete(playlist)
            }
            onDelete()
        }
    )

    val mediaItems = songs.map { it.asMediaItem }
    val youtubeMusicNotInstalledMessage = stringResource(R.string.youtube_music_not_installed)
    val (currentMediaId, playing) = playingSong(binder)

    val mosaicUrls = remember(songs) {
        songs.mapNotNull { it.thumbnailUrl?.takeIf { u -> u.isNotEmpty() } }.take(4)
    }

    CollapsingHeader(
        title = playlist.name,
        lazyListState = reorderingState.lazyListState,
        headerActions = {
            PlaylistHeaderActions(
                shareUrl = playlist.browseId?.let {
                    "https://music.youtube.com/playlist?list=${it.removePrefix("VL")}"
                },
                menuVisible = isMenuVisible,
                onMenuVisibleChange = { isMenuVisible = it },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                playlist.browseId?.let { browseId ->
                    NewMenuEntry(
                        icon = R.drawable.sync,
                        text = stringResource(R.string.sync),
                        enabled = !loading,
                        onClick = {
                            isMenuVisible = false
                            coroutineScope.launch {
                                loading = true
                                sync(playlist, browseId)
                                loading = false
                            }
                        }
                    )

                    songs.firstOrNull()?.let { firstSong ->
                        NewMenuEntry(
                            icon = R.drawable.play,
                            text = stringResource(R.string.watch_playlist_on_youtube),
                            onClick = {
                                isMenuVisible = false
                                binder?.player?.pause()
                                uriHandler.openUri(
                                    "https://youtube.com/watch?v=${firstSong.id}&list=${browseId.drop(2)}"
                                )
                            }
                        )

                        NewMenuEntry(
                            icon = R.drawable.musical_notes,
                            text = stringResource(R.string.open_in_youtube_music),
                            onClick = {
                                isMenuVisible = false
                                binder?.player?.pause()
                                if (!launchYouTubeMusic(
                                        context = context,
                                        endpoint = "watch?v=${firstSong.id}&list=${browseId.drop(2)}"
                                    )
                                ) {
                                    context.toast(youtubeMusicNotInstalledMessage)
                                }
                            }
                        )
                    }
                }

                NewMenuEntry(
                    icon = R.drawable.pencil,
                    text = stringResource(R.string.rename),
                    onClick = {
                        isMenuVisible = false
                        isRenaming = true
                    }
                )

                NewMenuEntry(
                    icon = R.drawable.trash,
                    text = stringResource(R.string.delete),
                    onClick = {
                        isMenuVisible = false
                        isDeleting = true
                    }
                )
            }
        }
    ) {
Box(modifier = modifier) {
        PlaylistBackdrop(mosaicUrls, reorderingState.lazyListState)
        PlaylistGradient()

        LookaheadScope {
            LazyColumn(
                state = reorderingState.lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                    .asPaddingValues(),
                modifier = Modifier.fillMaxSize()
            ) {
                item(
                    key = "header",
                    contentType = 0
                ) {
                    PlaylistHero(
                        title = playlist.name,
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
                        // No add-to-playlist flow yet, so the button is left out.
                        onAdd = null
                    )
                }

                itemsIndexed(
                    items = songs,
                    key = { _, song -> song.id },
                    contentType = { _, song -> song }
                ) { index, song ->
                    SongItem(
                        modifier = Modifier
                            .combinedClickable(
                                onLongClick = {
                                    menuState.display {
                                        InPlaylistMediaItemMenu(
                                            playlistId = playlist.id,
                                            positionInPlaylist = index,
                                            song = song,
                                            onDismiss = menuState::hide
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
                            )
                            .animateItemPlacement(reorderingState)
                            .draggedItem(
                                reorderingState = reorderingState,
                                index = index
                            ),
                        song = song,
                        thumbnailSize = Dimensions.thumbnails.song,
                        trailingContent = {
                            ReorderHandle(
                                reorderingState = reorderingState,
                                index = index,
                                color = colorPalette.accent
                            )
                        },
                        clip = !reorderingState.isDragging,
                        isPlaying = playing && currentMediaId == song.id
                    )
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(
            lazyListState = lazyListState,
            visible = !reorderingState.isDragging
        )
        }
    }
}

private suspend fun sync(
    playlist: Playlist,
    browseId: String
) = runCatching {
    Innertube.playlistPage(
        BrowseBody(browseId = browseId)
    )?.completed()?.getOrNull()?.let { remotePlaylist ->
        transaction {
            Database.clearPlaylist(playlist.id)

            remotePlaylist.songsPage
                ?.items
                ?.map { it.asMediaItem }
                ?.onEach { Database.insert(it) }
                ?.mapIndexed { position, mediaItem ->
                    SongPlaylistMap(
                        songId = mediaItem.mediaId,
                        playlistId = playlist.id,
                        position = position
                    )
                }
                ?.let(Database::insertSongPlaylistMaps)
        }
    }
}.onFailure {
    if (it is CancellationException) throw it
    it.printStackTrace()
}
