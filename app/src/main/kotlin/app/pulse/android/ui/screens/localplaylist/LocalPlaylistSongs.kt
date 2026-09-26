package app.pulse.android.ui.screens.localplaylist

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import app.pulse.android.ui.components.NewMenu
import app.pulse.android.ui.components.NewMenuEntry
import app.pulse.android.ui.components.themed.CollapsingHeader
import app.pulse.android.ui.components.themed.ConfirmationDialog
import app.pulse.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.pulse.android.ui.components.themed.HeaderCircleIconButton
import app.pulse.android.ui.components.themed.HeaderPillRow
import app.pulse.android.ui.components.themed.IconButton
import app.pulse.android.ui.components.themed.MosaicThumbnail
import app.pulse.android.ui.components.themed.QuadrantTint
import app.pulse.android.ui.components.themed.InPlaylistMediaItemMenu
import app.pulse.android.ui.components.themed.ReorderHandle
import app.pulse.android.ui.components.themed.TextFieldDialog
import app.pulse.android.ui.items.SongItem
import app.pulse.android.utils.asMediaItem
import app.pulse.android.utils.completed
import app.pulse.android.utils.forcePlayAtIndex
import app.pulse.android.utils.forcePlayFromBeginning
import app.pulse.android.utils.launchYouTubeMusic
import app.pulse.android.utils.medium
import app.pulse.android.utils.playingSong
import app.pulse.android.utils.semiBold
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

private val HeroShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalPlaylistSongs(
    playlist: Playlist,
    songs: ImmutableList<Song>,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
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

    var tintColor by remember { mutableStateOf(colorPalette.accent) }

    CollapsingHeader(
        title = playlist.name,
        lazyListState = reorderingState.lazyListState,
        headerActions = {
            HeaderPillRow(containerColor = tintColor, modifier = Modifier.padding(end = 8.dp)) {
                IconButton(
                    icon = R.drawable.share_social,
                    onClick = {
                        val url = playlist.browseId?.let {
                            "https://music.youtube.com/playlist?list=${it.removePrefix("VL")}"
                        }
                        url?.let {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, it)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, null))
                        }
                    }
                )
                Box {
                    IconButton(
                        icon = R.drawable.ellipsis_horizontal,
                        onClick = { isMenuVisible = !isMenuVisible }
                    )

                    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                        NewMenu(
                            visible = isMenuVisible,
                            onDismiss = { isMenuVisible = false },
                            containerColor = tintColor
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
                }
            }
        }
    ) {
Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.4f to Color.Transparent,
                        0.75f to colorPalette.background0,
                        1f to colorPalette.background0
                    )
                )
        )

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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 120.dp, bottom = 24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (mosaicUrls.isNotEmpty()) QuadrantTint(
                                urls = mosaicUrls,
                                onColorExtracted = { tintColor = it },
                                modifier = Modifier
                                    .fillMaxSize()
.graphicsLayer {
                                        scaleX = 2f
                                        scaleY = 4f
                                        clip = false
                                    }
                            )
                            MosaicThumbnail(
                                urls = mosaicUrls,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        shape = HeroShape
                                        shadowElevation = 8.dp.toPx()
                                        clip = false
                                    }
                                    .clip(HeroShape)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        BasicText(
                            text = playlist.name,
                            style = typography.l.semiBold.copy(color = colorPalette.text),
                            maxLines = 2,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )

                        BasicText(
                            text = pluralStringResource(
                                R.plurals.song_count_plural,
                                songs.size,
                                songs.size
                            ),
                            style = typography.s.medium.copy(color = tintColor.copy(alpha = 0.7f)),
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Spacer(modifier = Modifier.weight(1f))

                            HeaderCircleIconButton(
                                icon = R.drawable.shuffle,
                                containerColor = tintColor,
                                enabled = songs.isNotEmpty(),
                                onClick = {
                                    binder?.stopRadio()
                                    binder?.player?.forcePlayAtIndex(
                                        items = mediaItems.shuffled(),
                                        index = 0
                                    )
                                }
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(tintColor)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        enabled = songs.isNotEmpty()
                                    ) {
                                        binder?.stopRadio()
                                        binder?.player?.forcePlayFromBeginning(mediaItems)
                                    }
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(colorPalette.background0),
                                    modifier = Modifier.size(18.dp)
                                )

                                BasicText(
                                    text = stringResource(R.string.play),
                                    style = typography.s.semiBold.copy(color = colorPalette.background0)
                                )
                            }

                            HeaderCircleIconButton(
                                icon = R.drawable.add,
                                containerColor = tintColor,
                                onClick = {
                                    // ponytail: add-to-playlist flow — stub, wire when function phase starts
                                }
                            )

                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
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
                                color = tintColor
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
