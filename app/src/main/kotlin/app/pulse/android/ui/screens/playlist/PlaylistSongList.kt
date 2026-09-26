package app.pulse.android.ui.screens.playlist

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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
import app.pulse.android.query
import app.pulse.android.transaction
import app.pulse.android.ui.components.LocalMenuState
import app.pulse.android.ui.components.NewMenu
import app.pulse.android.ui.components.NewMenuEntry
import app.pulse.android.ui.components.ShimmerHost
import app.pulse.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.pulse.android.ui.components.themed.CollapsingHeader
import app.pulse.android.ui.components.themed.HeaderCircleIconButton
import app.pulse.android.ui.components.themed.HeaderPillRow
import app.pulse.android.ui.components.themed.IconButton
import app.pulse.android.ui.components.themed.MosaicThumbnail
import app.pulse.android.ui.components.themed.QuadrantTint
import app.pulse.android.ui.components.themed.NonQueuedMediaItemMenu
import app.pulse.android.ui.components.themed.TextFieldDialog
import app.pulse.android.ui.items.SongItem
import app.pulse.android.ui.items.SongItemPlaceholder
import app.pulse.android.utils.asMediaItem
import app.pulse.android.utils.completed
import app.pulse.android.utils.forcePlayAtIndex
import app.pulse.android.utils.forcePlayFromBeginning
import app.pulse.android.utils.medium
import app.pulse.android.utils.playingSong
import app.pulse.android.utils.semiBold
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

private val HeroShape = RoundedCornerShape(16.dp)

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
    val context = LocalContext.current
    val menuState = LocalMenuState.current

    var playlistPage by persist<Innertube.PlaylistOrAlbumPage?>("playlist/$browseId/playlistPage")

    LaunchedEffect(Unit) {
        if (playlistPage != null && playlistPage?.songsPage?.continuation == null) return@LaunchedEffect

        playlistPage = withContext(Dispatchers.IO) {
            Innertube
                .playlistPage(BrowseBody(browseId = browseId, params = params))
                ?.completed(
                    maxDepth = maxDepth ?: Int.MAX_VALUE,
                    shouldDedup = shouldDedup
                )
                ?.getOrNull()
        }
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

    var tintColor by remember { mutableStateOf(colorPalette.accent) }

    val lazyListState = rememberLazyListState()

    CollapsingHeader(
        title = playlistPage?.title ?: stringResource(R.string.unknown),
        lazyListState = lazyListState,
        headerActions = {
            HeaderPillRow(containerColor = tintColor, modifier = Modifier.padding(end = 8.dp)) {
                IconButton(
                    icon = R.drawable.share_social,
                    onClick = {
                        val url = playlistPage?.url
                            ?: "https://music.youtube.com/playlist?list=${browseId.removePrefix("VL")}"
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
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
                } else {
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
                            text = playlistPage?.title ?: stringResource(R.string.unknown),
                            style = typography.l.semiBold.copy(color = colorPalette.text),
                            maxLines = 2,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )

                        BasicText(
                            text = pluralStringResource(
                                R.plurals.song_count_plural,
                                songs?.size ?: 0,
                                songs?.size ?: 0
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
                                enabled = mediaItems?.isNotEmpty() == true,
                                onClick = {
                                    mediaItems?.let { items ->
                                        if (items.isNotEmpty()) {
                                            binder?.stopRadio()
                                            binder?.player?.forcePlayAtIndex(
                                                items = items.shuffled(),
                                                index = 0
                                            )
                                        }
                                    }
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
                                        enabled = mediaItems?.isNotEmpty() == true
                                    ) {
                                        mediaItems?.let { items ->
                                            if (items.isNotEmpty()) {
                                                binder?.stopRadio()
                                                binder?.player?.forcePlayFromBeginning(items)
                                            }
                                        }
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
                                onClick = { isImportingPlaylist = true }
                            )

                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
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

            if (playlistPage == null) item(key = "loading") {
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
