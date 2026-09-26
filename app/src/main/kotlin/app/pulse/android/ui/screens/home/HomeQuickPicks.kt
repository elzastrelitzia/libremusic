package app.pulse.android.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import app.pulse.android.Database
import app.pulse.android.LocalPlayerAwareWindowInsets
import app.pulse.android.LocalPlayerServiceBinder
import app.pulse.android.R
import app.pulse.core.data.models.Song
import app.pulse.core.data.models.toSong
import app.pulse.android.preferences.DataPreferences
import app.pulse.android.query
import app.pulse.android.ui.components.LocalMenuState
import app.pulse.android.ui.components.ShimmerHost
import app.pulse.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.pulse.android.ui.components.themed.NonQueuedMediaItemMenu
import app.pulse.android.ui.components.themed.TextPlaceholder
import app.pulse.android.ui.items.AlbumItem
import app.pulse.android.ui.items.AlbumItemPlaceholder
import app.pulse.android.ui.items.ArtistItem
import app.pulse.android.ui.items.ArtistItemPlaceholder
import app.pulse.android.ui.items.PlaylistItem
import app.pulse.android.ui.items.PlaylistItemPlaceholder
import app.pulse.android.ui.items.SongItem
import app.pulse.android.ui.items.SongItemPlaceholder
import app.pulse.android.ui.screens.Route
import app.pulse.android.ui.screens.settingsRoute
import app.pulse.android.ui.components.themed.HeaderCircleIconButton
import app.pulse.android.ui.components.themed.CollapsingHeader
import app.pulse.android.ui.components.themed.CollapsingHeaderContentSpacer
import app.pulse.android.utils.asMediaItem
import androidx.media3.common.MediaItem
import app.pulse.android.utils.center
import app.pulse.android.utils.forcePlay
import app.pulse.android.utils.playingSong
import app.pulse.android.utils.rememberSnapLayoutInfo
import app.pulse.android.utils.secondary
import app.pulse.android.utils.semiBold
import app.pulse.compose.persist.persist
import app.pulse.core.ui.Dimensions
import app.pulse.core.ui.LocalAppearance
import app.pulse.core.ui.utils.isLandscape
import app.pulse.providers.innertube.Innertube
import app.pulse.providers.innertube.models.NavigationEndpoint
import app.pulse.providers.innertube.models.bodies.NextBody
import app.pulse.providers.innertube.requests.relatedPage
import app.pulse.providers.innertube.requests.trendingCharts
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map


@OptIn(ExperimentalFoundationApi::class)
@Route
@Composable
fun QuickPicks(
    onAlbumClick: (Innertube.AlbumItem) -> Unit,
    onArtistClick: (Innertube.ArtistItem) -> Unit,
    onPlaylistClick: (Innertube.PlaylistItem) -> Unit,
) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    var trending by persist<Song?>("home/trending")

    var relatedPageResult by persist<Result<Innertube.RelatedPage?>?>(tag = "home/relatedPageResult")

    // Restore the disk cache first so a cold open renders instantly and
    // skips the network while the cache is fresh (TTL-configurable).
    val context = LocalContext.current.applicationContext
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Seed id for the related feed: current trending song, else charts head, else fallback.
    suspend fun seedId(): String =
        trending?.id ?: Innertube.trendingCharts()?.getOrNull()?.firstOrNull()?.key ?: "J7p4bzqLvCw"

    // force a fresh related page, bypassing the cache TTL.
    suspend fun refreshRelated() {
        relatedPageResult = Innertube.relatedPage(body = NextBody(videoId = seedId()))
        relatedPageResult?.getOrNull()?.let {
            HomeCache.saveRelated(context.filesDir, it)
            HomeCache.prefetchThumbs(context, null, it)
        }
    }

    LaunchedEffect(DataPreferences.quickPicksSource) {
        if (relatedPageResult == null) {
            val cached = HomeCache.restoreRelated(context.filesDir)
            if (cached?.getOrNull()?.songs?.isNotEmpty() == true) {
                relatedPageResult = cached
            } else if (cached != null) {
                // stale/empty cache delete so next restart fetches fresh.
                java.io.File(context.filesDir, "home/related.json").delete()
            }
        }
        // Warm the thumbnails so the cached feed renders fully offline.
        HomeCache.prefetchThumbs(context, null, relatedPageResult?.getOrNull())

        suspend fun handleSong(song: Song?) {
            var seedId = song?.id
            if (seedId == null && trending == null && relatedPageResult == null) {
                val chartsResult = Innertube.trendingCharts()
                chartsResult
                    ?.getOrNull()
                    ?.firstOrNull()
                    ?.let { fallback ->
                        seedId = fallback.key
                        trending = Song(
                            id = fallback.key,
                            title = fallback.info?.name ?: "",
                            durationText = fallback.durationText,
                            thumbnailUrl = fallback.thumbnail?.url
                        )
                    }
            }
            seedId = seedId ?: "J7p4bzqLvCw"
            val cachedEmpty = relatedPageResult?.getOrNull()?.songs.isNullOrEmpty()
            val shouldFetch = relatedPageResult == null || cachedEmpty || (trending != null && trending?.id != song?.id)
            if (shouldFetch) {
                relatedPageResult = Innertube.relatedPage(
                    body = NextBody(videoId = seedId)
                )
                // if seed returned empty content, retry with fallback seed.
                if (relatedPageResult?.getOrNull()?.songs.isNullOrEmpty() && seedId != "J7p4bzqLvCw") {
                    relatedPageResult = Innertube.relatedPage(
                        body = NextBody(videoId = "J7p4bzqLvCw")
                    )
                }
                // only cache if we got actual songs back.
                relatedPageResult?.getOrNull()?.takeIf { !it.songs.isNullOrEmpty() }?.let {
                    HomeCache.saveRelated(context.filesDir, it)
                    HomeCache.prefetchThumbs(context, null, it)
                }
            }
            if (song != null) trending = song
        }

        val sourceFlow = when (DataPreferences.quickPicksSource) {
            DataPreferences.QuickPicksSource.Trending -> Database.trending().map { it.firstOrNull() }
            DataPreferences.QuickPicksSource.LastInteraction -> Database.events().map { it.firstOrNull()?.song?.toSong() }
        }.distinctUntilChanged { old, new -> old?.id == new?.id }
        sourceFlow.collect {
            runCatching { handleSong(it) }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                }
        }
    }

    val scrollState = rememberScrollState()
    val quickPicksLazyGridState = rememberLazyGridState()

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)
        .padding(endPaddingValues)

    val (currentMediaId, playing) = playingSong(binder)


    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                refreshRelated()
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        BoxWithConstraints {
        val quickPicksLazyGridItemWidthFactor =
            if (isLandscape && maxWidth * 0.475f >= 320.dp) 0.475f else 0.75f

        val snapLayoutInfoProvider = rememberSnapLayoutInfo(
            lazyGridState = quickPicksLazyGridState,
            positionInLayout = { layoutSize, itemSize ->
                (layoutSize * quickPicksLazyGridItemWidthFactor / 2f - itemSize / 2f)
            }
        )

        val itemInHorizontalGridWidth = maxWidth * quickPicksLazyGridItemWidthFactor

        CollapsingHeader(
            title = stringResource(R.string.quick_picks),
            scrollState = scrollState,
            headerActions = {
                HeaderCircleIconButton(
                    icon = R.drawable.settings,
                    onClick = { settingsRoute.global() }
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .background(colorPalette.background0)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        windowInsets
                            .only(WindowInsetsSides.Vertical)
                            .asPaddingValues()
                    )
            ) {
                Spacer(modifier = Modifier.height(CollapsingHeaderContentSpacer))

            relatedPageResult?.getOrNull()?.let { related ->
                // ponytail: shared click handler extracted to avoid duplication
                fun playSong(mediaItem: MediaItem) {
                    binder?.stopRadio()
                    binder?.player?.forcePlay(mediaItem)
                    binder?.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
                }

                LazyHorizontalGrid(
                    state = quickPicksLazyGridState,
                    rows = GridCells.Fixed(4),
                    flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                    contentPadding = endPaddingValues,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((Dimensions.thumbnails.song + Dimensions.items.verticalPadding * 2) * 4)
                ) {
                    trending?.let { song ->
                        item {
                            SongItem(
                                modifier = Modifier
                                    .combinedClickable(
                                        onLongClick = {
                                            menuState.display {
                                                NonQueuedMediaItemMenu(
                                                    onDismiss = menuState::hide,
                                                    mediaItem = song.asMediaItem,
                                                    onRemoveFromQuickPicks = {
                                                        query { Database.clearEventsFor(song.id) }
                                                    }
                                                )
                                            }
                                        },
                                        onClick = { playSong(song.asMediaItem) }
                                    )
                                    .animateItem(fadeInSpec = null, fadeOutSpec = null)
                                    .width(itemInHorizontalGridWidth),
                                song = song,
                                thumbnailSize = Dimensions.thumbnails.song,
                                trailingContent = {
                                    Image(
                                        painter = painterResource(R.drawable.star),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(colorPalette.accent),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                showDuration = false,
                                isPlaying = playing && currentMediaId == song.id
                            )
                        }
                    }

                    items(
                        items = related.songs?.dropLast(if (trending == null) 0 else 1)
                            ?: emptyList(),
                        key = Innertube.SongItem::key
                    ) { song ->
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
                                    onClick = { playSong(song.asMediaItem) }
                                )
                                .animateItem(fadeInSpec = null, fadeOutSpec = null)
                                .width(itemInHorizontalGridWidth),
                            showDuration = false,
                            isPlaying = playing && currentMediaId == song.key
                        )
                    }
                }

                related.albums?.let { albums ->
                    BasicText(
                        text = stringResource(R.string.related_albums),
                        style = typography.m.semiBold,
                        modifier = sectionTextModifier
                    )

                    LazyRow(contentPadding = endPaddingValues) {
                        items(
                            items = albums,
                            key = Innertube.AlbumItem::key
                        ) { album ->
                            AlbumItem(
                                album = album,
                                thumbnailSize = Dimensions.thumbnails.album,
                                alternative = true,
                                modifier = Modifier.clickable { onAlbumClick(album) }
                            )
                        }
                    }
                }

                related.artists?.let { artists ->
                    BasicText(
                        text = stringResource(R.string.similar_artists),
                        style = typography.m.semiBold,
                        modifier = sectionTextModifier
                    )

                    LazyRow(contentPadding = endPaddingValues) {
                        items(
                            items = artists,
                            key = Innertube.ArtistItem::key
                        ) { artist ->
                            ArtistItem(
                                artist = artist,
                                thumbnailSize = Dimensions.thumbnails.artist,
                                alternative = true,
                                modifier = Modifier.clickable { onArtistClick(artist) }
                            )
                        }
                    }
                }

                related.playlists?.let { playlists ->
                    BasicText(
                        text = stringResource(R.string.recommended_playlists),
                        style = typography.m.semiBold,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp, bottom = 8.dp)
                    )

                    LazyRow(contentPadding = endPaddingValues) {
                        items(
                            items = playlists,
                            key = Innertube.PlaylistItem::key
                        ) { playlist ->
                            PlaylistItem(
                                playlist = playlist,
                                thumbnailSize = Dimensions.thumbnails.playlist,
                                alternative = true,
                                modifier = Modifier.clickable { onPlaylistClick(playlist) }
                            )
                        }
                    }
                }

            } ?: relatedPageResult?.exceptionOrNull()?.let {
                BasicText(
                    text = stringResource(R.string.error_message),
                    style = typography.s.secondary.center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(all = 16.dp)
                )
            } ?: ShimmerHost {
                repeat(4) {
                    SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        AlbumItemPlaceholder(
                            thumbnailSize = Dimensions.thumbnails.album,
                            alternative = true
                        )
                    }
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        ArtistItemPlaceholder(
                            thumbnailSize = Dimensions.thumbnails.album,
                            alternative = true
                        )
                    }
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        PlaylistItemPlaceholder(
                            thumbnailSize = Dimensions.thumbnails.album,
                            alternative = true
                        )
                    }
                }
            }
        }
        }

        FloatingActionsContainerWithScrollToTop(
            scrollState = scrollState,
            icon = null
        )
    }
    }
}
