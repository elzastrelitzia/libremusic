package app.pulse.android.ui.screens.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pulse.android.Database
import app.pulse.android.LocalPlayerAwareWindowInsets
import app.pulse.android.LocalPlayerServiceBinder
import app.pulse.android.R
import app.pulse.android.models.Playlist
import app.pulse.android.models.SongPlaylistMap
import app.pulse.android.query
import app.pulse.android.transaction
import app.pulse.android.ui.components.LocalMenuState
import app.pulse.android.ui.components.NewMenuEntry
import app.pulse.android.ui.components.ShimmerHost
import app.pulse.android.ui.components.themed.CollapsingHeader
import app.pulse.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.pulse.android.ui.components.themed.NonQueuedMediaItemMenu
import app.pulse.android.ui.components.themed.PlaylistBackdrop
import app.pulse.android.ui.components.themed.PlaylistGradient
import app.pulse.android.ui.components.themed.PlaylistHeaderActions
import app.pulse.android.ui.components.themed.PlaylistHero
import app.pulse.android.ui.components.themed.SecondaryTextButton
import app.pulse.android.ui.components.themed.TextFieldDialog
import app.pulse.android.ui.items.SongItem
import app.pulse.android.ui.items.SongItemPlaceholder
import app.pulse.android.utils.asMediaItem
import app.pulse.android.utils.completed
import app.pulse.android.utils.forcePlayAtIndex
import app.pulse.android.utils.forcePlayFromBeginning
import app.pulse.android.utils.medium
import app.pulse.android.utils.playingSong
import app.pulse.compose.persist.persist
import app.pulse.core.ui.Dimensions
import app.pulse.core.ui.LocalAppearance
import app.pulse.core.ui.shimmer
import app.pulse.providers.innertube.Innertube
import app.pulse.providers.innertube.models.bodies.BrowseBody
import app.pulse.providers.innertube.requests.playlistPage
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistSongList(
    browseId: String,
    params: String?,
    maxDepth: Int?,
    shouldDedup: Boolean,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current

    var playlistPage by persist<Innertube.PlaylistOrAlbumPage?>("playlist/$browseId/playlistPage")

    // A failed fetch used to leave the skeleton up forever: the effect was keyed on
    // Unit, so it never retried and there was no error branch.
    var loadFailed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        if (playlistPage != null && playlistPage?.songsPage?.continuation == null) return@LaunchedEffect

        loadFailed = false
        val page = withContext(Dispatchers.IO) {
            Innertube
                .playlistPage(BrowseBody(browseId = browseId, params = params))
                ?.completed(
                    maxDepth = maxDepth ?: Int.MAX_VALUE,
                    shouldDedup = shouldDedup
                )
                ?.getOrNull()
        }
        loadFailed = page == null
        playlistPage = page
    }

    var isImportingPlaylist by rememberSaveable { mutableStateOf(false) }

    if (isImportingPlaylist) TextFieldDialog(
        hintText = stringResource(R.string.enter_playlist_name_prompt),
        initialTextInput = playlistPage?.title.orEmpty(),
        onDismiss = { isImportingPlaylist = false },
        onAccept = { text ->
            query {
                transaction {
                    val playlistId = Database.insert(
                        Playlist(
                            name = text,
                            browseId = browseId,
                            thumbnail = playlistPage?.thumbnail?.url
                        )
                    )

                    playlistPage?.songsPage?.items
                        ?.map(Innertube.SongItem::asMediaItem)
                        ?.onEach(Database::insert)
                        ?.mapIndexed { index, mediaItem ->
                            SongPlaylistMap(
                                songId = mediaItem.mediaId,
                                playlistId = playlistId,
                                position = index
                            )
                        }?.let(Database::insertSongPlaylistMaps)
                }
            }
        }
    )

    var isMenuVisible by rememberSaveable { mutableStateOf(false) }

    val songs = playlistPage?.songsPage?.items
    val mediaItems = songs?.map { it.asMediaItem }

    val mosaicUrls = remember(songs, playlistPage?.thumbnail?.url) {
        (
            songs?.mapNotNull { it.thumbnail?.url?.takeIf { u -> u.isNotEmpty() } } ?: emptyList()
            ).ifEmpty { listOfNotNull(playlistPage?.thumbnail?.url) }
            .take(4)
    }

    val (currentMediaId, playing) = playingSong(binder)

    val lazyListState = rememberLazyListState()

    CollapsingHeader(
        title = playlistPage?.title ?: stringResource(R.string.unknown),
        lazyListState = lazyListState,
        headerActions = {
            PlaylistHeaderActions(
                shareUrl = playlistPage?.url
                    ?: "https://music.youtube.com/playlist?list=${browseId.removePrefix("VL")}",
                menuVisible = isMenuVisible,
                onMenuVisibleChange = { isMenuVisible = it },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                NewMenuEntry(
                    icon = R.drawable.add,
                    text = stringResource(R.string.import_playlist),
                    onClick = {
                        isMenuVisible = false
                        isImportingPlaylist = true
                    }
                )
            }
        }
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
                if (playlistPage == null) {
                    if (loadFailed) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 128.dp, bottom = 24.dp)
                        ) {
                            BasicText(
                                text = stringResource(R.string.error_message),
                                style = typography.m.medium.copy(color = colorPalette.textSecondary)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            SecondaryTextButton(
                                text = stringResource(R.string.reload),
                                onClick = { reloadKey++ }
                            )
                        }
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 128.dp, bottom = 24.dp)
                                .shimmer()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .aspectRatio(1f)
                                    .background(colorPalette.shimmer)
                            )
                        }
                    }
                } else {
                    PlaylistHero(
                        title = playlistPage?.title ?: stringResource(R.string.unknown),
                        songCount = songs?.size ?: 0,
                        mosaicUrls = mosaicUrls,
                        onShuffle = {
                            val items = mediaItems
                            if (!items.isNullOrEmpty()) {
                                binder?.stopRadio()
                                binder?.player?.forcePlayAtIndex(
                                    items = items.shuffled(),
                                    index = 0
                                )
                            }
                        },
                        onPlay = {
                            val items = mediaItems
                            if (!items.isNullOrEmpty()) {
                                binder?.stopRadio()
                                binder?.player?.forcePlayFromBeginning(items)
                            }
                        },
                        onAdd = { isImportingPlaylist = true }
                    )
                }
            }

            itemsIndexed(items = songs ?: emptyList()) { index, song ->
                SongItem(
                    song = song,
                    thumbnailSize = Dimensions.thumbnails.song,
                    modifier = Modifier
                        .combinedClickable(
                            onLongClick = {
                                menuState.display {
                                    NonQueuedMediaItemMenu(
                                        onDismiss = menuState::hide,
                                        mediaItem = song.asMediaItem
                                    )
                                }
                            },
                            onClick = {
                                mediaItems?.let { items ->
                                    binder?.stopRadio()
                                    binder?.player?.forcePlayAtIndex(items, index)
                                }
                            }
                        ),
                    isPlaying = playing && currentMediaId == song.key
                )
            }

            if (playlistPage == null && !loadFailed) item(key = "loading") {
                ShimmerHost(modifier = Modifier.fillParentMaxSize()) {
                    repeat(4) {
                        SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                    }
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(
            lazyListState = lazyListState
        )
        }
    }
}
