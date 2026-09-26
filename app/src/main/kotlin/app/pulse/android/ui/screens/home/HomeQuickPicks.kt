package app.pulse.android.ui.screens.home

import android.util.Log
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
import app.pulse.core.data.utils.songBundle
import app.pulse.android.preferences.DataPreferences
import app.pulse.android.service.isLocal
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val TAG = "QuickPicks"

// Known-good seed for when the wanted one has no recommendations at all.
private const val FALLBACK_SEED = "J7p4bzqLvCw"

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

    // Seed the shown feed was built from. Persisted alongside the page because a tab
    // change disposes this composable while the persist map survives, and a plain
    // remember would come back null, which is exactly the value that makes shouldFetch
    // refetch. The page came back and the seed did not, so every return to the tab paid
    // for the same page twice.
    var feedSeed by persist<String>(tag = "home/feedSeed")

    // Restore the disk cache first so a cold open renders instantly and
    // skips the network while the cache is fresh (TTL-configurable).
    val context = LocalContext.current.applicationContext
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Seed id for the related feed: current trending song, else charts head, else fallback.
    suspend fun seedId(): String =
        trending?.id ?: Innertube.trendingCharts()?.getOrNull()?.firstOrNull()?.key ?: FALLBACK_SEED

    // SongBundle only carries durationText and explicit (see Song.asMediaItem), so
    // the rest comes off the media metadata. Same shape the charts fallback builds
    // by hand below.
    fun MediaItem.toSong() = Song(
        id = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artistsText = mediaMetadata.artist?.toString(),
        durationText = mediaMetadata.extras?.songBundle?.durationText,
        thumbnailUrl = mediaMetadata.artworkUri?.toString(),
        explicit = mediaMetadata.extras?.songBundle?.explicit ?: false
    )

    // force a fresh related page, bypassing the cache TTL.
    suspend fun refreshRelated() {
        val seed = seedId()
        val first = Innertube.relatedPage(body = NextBody(videoId = seed))
        // An obscure seed can come back with no recommendations at all. Retry once
        // with the fallback seed, and only publish the final answer, so a dead
        // attempt never flashes the error message before the retry lands.
        val retried = first?.getOrNull()?.songs.isNullOrEmpty() && seed != FALLBACK_SEED
        val result = if (retried) {
            Innertube.relatedPage(body = NextBody(videoId = FALLBACK_SEED))
        } else first
        relatedPageResult = result
        // the retry answered for the fallback, so that is the seed the feed belongs to
        feedSeed = if (retried) FALLBACK_SEED else seed
        Log.d(TAG, "refreshed seed=$seed from=${feedSeed} songs=${result?.getOrNull()?.songs?.size} err=${result?.exceptionOrNull()}")
        // only cache a page that actually has songs, so a bad refresh cannot
        // overwrite a good disk cache with an empty one.
        result?.getOrNull()?.takeIf { !it.songs.isNullOrEmpty() }?.let {
            HomeCache.saveRelated(context.filesDir, feedSeed, it)
            HomeCache.prefetchThumbs(context, null, it)
        }
    }

    // binder is a key because it arrives from onServiceConnected, after this effect
    // has already started. LocalPlayerServiceBinder is a staticCompositionLocalOf, so
    // reading it here is not a snapshot read and cannot be waited on with snapshotFlow.
    //
    // relatedPageResult is a key too, and that is the load bearing one. persist builds
    // its state inside remember(persistMap, tag) with getOrPut, so the object can be
    // replaced while the composition lives on (PersistMapCleanup wipes the "home/"
    // prefix). The running effect then holds the orphan and publishes into it, the
    // render reads the fresh null, and the grid sits on the shimmer forever even
    // though the fetch succeeded. Keying on the state restarts the effect against the
    // object the render actually reads, and cancels the orphaned run so its in-flight
    // request cannot land a duplicate answer.
    LaunchedEffect(DataPreferences.quickPicksSource, binder, relatedPageResult) {
        suspend fun handleSong(song: Song?) {
            var seedId = song?.id
            // No relatedPageResult check here: the restore publishes a page before this
            // runs, so guarding on it skipped the charts cascade and left the hardcoded
            // fallback seed, which has no recommendations of its own.
            if (seedId == null && trending == null) {
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
            seedId = seedId ?: FALLBACK_SEED
            val cachedEmpty = relatedPageResult?.getOrNull()?.songs.isNullOrEmpty()
            // Compare the seed the feed was built from, not trending: trending is
            // in-memory only, so it is null on a cold start exactly when a stale
            // cache is most likely, and the old check then served the wrong feed.
            val shouldFetch = relatedPageResult == null || cachedEmpty || feedSeed != seedId
            if (shouldFetch) {
                relatedPageResult = Innertube.relatedPage(
                    body = NextBody(videoId = seedId)
                )
                // if seed returned empty content, retry with fallback seed.
                val retried = relatedPageResult?.getOrNull()?.songs.isNullOrEmpty() && seedId != FALLBACK_SEED
                if (retried) {
                    relatedPageResult = Innertube.relatedPage(
                        body = NextBody(videoId = FALLBACK_SEED)
                    )
                }
                // the retry answered for the fallback, so that is the seed the feed belongs to
                feedSeed = if (retried) FALLBACK_SEED else seedId
                Log.d(TAG, "fetched seed=$seedId from=$feedSeed songs=${relatedPageResult?.getOrNull()?.songs?.size} err=${relatedPageResult?.exceptionOrNull()}")
                // only cache if we got actual songs back.
                relatedPageResult?.getOrNull()?.takeIf { !it.songs.isNullOrEmpty() }?.let {
                    HomeCache.saveRelated(context.filesDir, feedSeed, it)
                    HomeCache.prefetchThumbs(context, null, it)
                }
            } else {
                Log.d(TAG, "served cache seed=$seedId from=$feedSeed songs=${relatedPageResult?.getOrNull()?.songs?.size}")
            }
            if (song != null) trending = song
        }

        val sourceFlow = when (DataPreferences.quickPicksSource) {
            DataPreferences.QuickPicksSource.Trending -> Database.trending().map { it.firstOrNull() }
            DataPreferences.QuickPicksSource.LastInteraction -> Database.events().map { it.firstOrNull()?.song?.toSong() }
        }.distinctUntilChanged { old, new -> old?.id == new?.id }

        if (relatedPageResult == null) {
            val cached = HomeCache.restoreRelated(context.filesDir)
            val hit = cached?.getOrNull()
            if (hit?.page?.songs?.isNotEmpty() == true) {
                relatedPageResult = Result.success(hit.page)
                feedSeed = hit.seed
                Log.d(TAG, "restored disk cache from=${hit.seed} songs=${hit.page.songs?.size}")
            } else if (cached != null) {
                // stale/empty cache delete so next restart fetches fresh.
                java.io.File(context.filesDir, "home/related.json").delete()
                Log.d(TAG, "discarded stale disk cache")
            }
        }
        // Warm the thumbnails so the cached feed renders fully offline.
        HomeCache.prefetchThumbs(context, null, relatedPageResult?.getOrNull())

        // First paint must not wait on the DB flow. A fresh install has no history,
        // and a swallowed throw in handleSong would leave relatedPageResult null and
        // the shimmer up forever with nothing in the log to explain it.
        runCatching { handleSong(trending) }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                Log.e(TAG, "initial quick picks load failed", it)
            }

        // Whatever is playing right now is the seed, so the feed follows the
        // listener instead of waiting for a track to finish and reach the Event
        // table. collectLatest so skipping through a queue cancels the fetch in
        // flight instead of stacking one request per skipped track.
        launch {
            binder?.mediaItemState?.collectLatest { mediaItem ->
                val song = mediaItem?.toSong() ?: return@collectLatest
                // A local file has no videoId, so there is no seed to ask YouTube
                // about and nothing to star that can lead to a recommendation.
                if (song.isLocal) return@collectLatest
                // mediaItemState replays the current item to every new collector, so
                // the binder restart replays it too. Skip when it already seeded the
                // feed, otherwise the restart costs a request for the same song.
                if (song.id == feedSeed) return@collectLatest
                runCatching {
                    trending = song
                    refreshRelated()
                }.onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    Log.e(TAG, "quick picks refresh failed for ${song.id}", it)
                }
            }
        }

        sourceFlow.collect { song ->
            // No song means no history. The load above already covered that, and
            // handleSong would refetch here because trending is set and song is not.
            if (song == null) return@collect
            // An item already loaded wins, otherwise this races the collect
            // above and the loser of the two fetches decides the feed.
            if (binder?.mediaItemState?.value != null) return@collect
            runCatching { handleSong(song) }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    Log.e(TAG, "quick picks source update failed", it)
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
                        items = related.songs ?: emptyList(),
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

            // reached only when getOrNull() is null, so this catches both a real
            // failure and "loaded, but YouTube Music had no recommendations".
            // Without it, Result.success(null) fell through to the shimmer below
            // and spun forever.
            } ?: relatedPageResult?.let {
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
