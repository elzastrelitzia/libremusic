package app.pulse.desktop.ui.screens.home

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pulse.core.data.models.Song
import app.pulse.desktop.ui.constants.sizes.CardSizes
import app.pulse.desktop.ui.constants.sizes.Sizes
import app.pulse.core.data.utils.toSong
import app.pulse.desktop.ui.components.HomeCard
import app.pulse.desktop.ui.components.MoodsSkeleton
import app.pulse.desktop.ui.utils.NetworkImage
import app.pulse.desktop.ui.components.NewReleasesSkeleton
import app.pulse.desktop.ui.components.QuickPicksSkeleton
import app.pulse.desktop.ui.components.TrendingSkeleton
import app.pulse.desktop.ui.utils.log
import app.pulse.core.data.utils.AppDirs
import app.pulse.providers.innertube.Innertube
import app.pulse.providers.innertube.models.bodies.NextBody
import app.pulse.providers.innertube.requests.discoverPage
import app.pulse.providers.innertube.requests.relatedPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val fCacheJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

// disk-persisted cache
private object HomeCache {
    var discover: Result<Innertube.DiscoverPage>? = null
    var related: Result<Innertube.RelatedPage?>? = null

    private val cacheDir = AppDirs.home
    private val discoverFile = File(cacheDir, "discover.json")
    private val relatedFile = File(cacheDir, "related.json")
    private val ttlMs = 30L * 60 * 1000 // ttl 30 min

    @Serializable
    data class RelatedData(val page: Innertube.RelatedPage? = null)
    @Serializable
    data class DiscoverData(val page: Innertube.DiscoverPage? = null)

    fun loadFromDisk() {
        try {
            if (discoverFile.exists()) {
                val age = System.currentTimeMillis() - discoverFile.lastModified()
                if (age <= ttlMs) {
                    val text = discoverFile.readText()
                    val data = fCacheJson.decodeFromString<DiscoverData>(text)
                    if (data.page != null) {
                        discover = Result.success(data.page)
                        log("HomeCache", "disk: restored discover (moods=${data.page.moods.size})")
                    }
                } else {
                    discoverFile.delete()
                }
            }
        } catch (e: Exception) {
            log("HomeCache", "disk: discover cache error: ${e.message}")
            discoverFile.delete()
        }
        try {
            if (relatedFile.exists()) {
                val age = System.currentTimeMillis() - relatedFile.lastModified()
                if (age <= ttlMs) {
                    val text = relatedFile.readText()
                    val data = fCacheJson.decodeFromString<RelatedData>(text)
                    if (data.page != null) {
                        related = Result.success(data.page)
                        log("HomeCache", "disk: restored related (songs=${data.page.songs?.size})")
                    }
                } else {
                    relatedFile.delete()
                }
            }
        } catch (e: Exception) {
            log("HomeCache", "disk: related cache error: ${e.message}")
            relatedFile.delete()
        }
    }

    fun saveToDisk() {
        cacheDir.mkdirs()
        // save discover
        val d = discover
        if (d?.isSuccess == true && d.getOrNull() != null) {
            try {
                val data = DiscoverData(page = d.getOrNull())
                discoverFile.writeText(fCacheJson.encodeToString(data))
                log("HomeCache", "disk: saved discover page")
            } catch (e: Exception) {
                log("HomeCache", "disk: failed to save discover: ${e.message}")
            }
        }
        // save related
        val r = related
        if (r?.isSuccess == true && r.getOrNull() != null) {
            try {
                val data = RelatedData(page = r.getOrNull())
                relatedFile.writeText(fCacheJson.encodeToString(data))
                log("HomeCache", "disk: saved related page")
            } catch (e: Exception) {
                log("HomeCache", "disk: failed to save related: ${e.message}")
            }
        }
    }
}

@Composable
fun HomeScreen(
    page: Innertube.DiscoverPage?,
    onPageLoaded: (Result<Innertube.DiscoverPage>) -> Unit,
    onPlaySong: (Song) -> Unit,
    onMoreMoods: () -> Unit = {},
    onMoreAlbums: () -> Unit = {},
    onMoreTrending: () -> Unit = {}
) {
    // disk cache loads once per JVM session
    remember { HomeCache.loadFromDisk() }

    // init from cache if parent didn't provide fresh page
    var discoverResult by remember {
        mutableStateOf(page?.let { Result.success(it) } ?: HomeCache.discover)
    }
    var relatedResult by remember {
        mutableStateOf<Result<Innertube.RelatedPage?>?>(HomeCache.related)
    }

    // retry with exponential backoff: 1s, 2s, 4s
    // returns null when all attempts exhausted (last error is logged)
    suspend fun <T> retryWithBackoff(
        maxRetries: Int = 2,
        initialDelayMs: Long = 1000L,
        label: String = "",
        block: suspend () -> Result<T>?
    ): Result<T>? {
        var attempt = 0
        while (true) {
            val result = block()
            if (result?.isSuccess == true) return result
            attempt++
            if (attempt > maxRetries) {
                log("HomeScreen", "$label exhausted after $attempt attempts")
                return result
            }
            val delayMs = initialDelayMs * (1L shl attempt)
            log("HomeScreen", "$label attempt $attempt failed, retry in ${delayMs}ms")
            delay(delayMs)
        }
    }

    // parallel fetch: relatedPage starts with fallback seed while discoverPage loads
    LaunchedEffect(Unit) {
        log("HomeScreen", "initial: page=${page != null}, discoverLoaded=${discoverResult?.isSuccess}, relatedLoaded=${relatedResult?.isSuccess}")

        // start discover fetch in background (parallel)
        val discoverJob = launch {
            if (discoverResult?.isSuccess != true) {
                log("HomeScreen", "fetching discoverPage...")
                discoverResult = retryWithBackoff(label = "discoverPage") {
                    Innertube.discoverPage()
                }
                val d = discoverResult
                if (d?.isSuccess == true) {
                    val p = d.getOrNull()
                    log("HomeScreen", "discoverPage OK: moods=${p?.moods?.size}, newReleases=${p?.newReleaseAlbums?.size}, trending=${p?.trending?.songs?.size}")
                    HomeCache.discover = d
                    HomeCache.saveToDisk()
                } else {
                    log("HomeScreen", "discoverPage FAILED: ${d?.exceptionOrNull()?.message}")
                }
                discoverResult?.let { onPageLoaded(it) }
            }
        }

        // start relatedPage with fallback seed IMMEDIATELY (parallel with discover)
        if (relatedResult?.isSuccess != true) {
            log("HomeScreen", "fetching relatedPage with fallback seed (parallel)...")
            val fallbackResult = retryWithBackoff(maxRetries = 1, label = "relatedPage(fallback)") {
                Innertube.relatedPage(body = NextBody(videoId = "J7p4bzqLvCw"))
            }
            if (fallbackResult?.isSuccess == true && fallbackResult.getOrNull() != null) {
                val page = fallbackResult.getOrNull()!!
                log("HomeScreen", "fallback OK: songs=${page.songs?.size}, albums=${page.albums?.size}, artists=${page.artists?.size}, playlists=${page.playlists?.size}")
                relatedResult = fallbackResult
                HomeCache.related = fallbackResult
                HomeCache.saveToDisk()
            }
        }

        // wait for discover to finish, then try real seeds if fallback failed
        discoverJob.join()

        val seeds = discoverResult?.getOrNull()?.trending?.songs?.take(3)?.map { it.key }.orEmpty()
        if (seeds.isNotEmpty() && relatedResult?.isSuccess != true) {
            log("HomeScreen", "trying ${seeds.size} real seeds: $seeds")
            for (seed in seeds) {
                if (relatedResult?.isSuccess == true) break
                val result = retryWithBackoff(maxRetries = 1, label = "relatedPage($seed)") {
                    Innertube.relatedPage(body = NextBody(videoId = seed))
                }
                if (result?.isSuccess == true && result.getOrNull() != null) {
                    val page = result.getOrNull()!!
                    log("HomeScreen", "relatedPage OK from seed=$seed: songs=${page.songs?.size}, albums=${page.albums?.size}, artists=${page.artists?.size}, playlists=${page.playlists?.size}")
                    relatedResult = result
                    HomeCache.related = result
                    HomeCache.saveToDisk()
                    break
                }
                if (result?.isSuccess == true && result.getOrNull() == null) {
                    log("HomeScreen", "seed=$seed returned null, trying next...")
                } else {
                    log("HomeScreen", "seed=$seed FAILED: ${result?.exceptionOrNull()?.message}, trying next...")
                }
            }
        }

        // mark complete if still nothing
        if (relatedResult?.isSuccess != true) {
            log("HomeScreen", "all exhausted, marking related as unavailable")
            relatedResult = Result.success(null)
        }
    }

    val loadedPage = discoverResult?.getOrNull()

    val bg = Color(0xFF0a0a0a)
    val text = Color(0xFFf2f0eb)
    val dim = Color(0xFFa8a39a)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        val scrollState = rememberScrollState()
        val sbStyle = remember {
            ScrollbarStyle(
                minimalHeight = Sizes.scrollMinH.dp,
                thickness = Sizes.scrollThickness.dp,
                shape = RoundedCornerShape(Sizes.scrollRadius.dp),
                hoverDurationMillis = 300,
                unhoverColor = Color(0xFF5a5a5a).copy(alpha = 0.2f),
                hoverColor = Color(0xFF8a8a8a).copy(alpha = 0.6f)

            )
        }

        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(start = Sizes.homeColumnPad.dp, end = Sizes.homeColumnPad.dp, top = Sizes.homeColumnPad.dp, bottom = Sizes.homeColumnBottomPad.dp)
            ) {
            // quick picks
            relatedResult?.getOrNull()?.let { related ->
                val qpSongs = related.songs
                if (qpSongs != null && qpSongs.isNotEmpty()) {
                    SectionHeader("Quick Picks", {})
                    Spacer(Modifier.height(CardSizes.gapSm.dp))
                    CarouselRow {
                        qpSongs.take(20).forEach { songItem ->
                            val song = songItem.toSong()
                            CompactSongCard(
                                song = song,
                                text = text,
                                dim = dim,
                                onClick = { onPlaySong(song) }
                            )
                        }
                    }
                    Spacer(Modifier.height(CardSizes.gapLg.dp))
                }

                val qpAlbums = related.albums
                if (qpAlbums != null && qpAlbums.isNotEmpty()) {
                    SectionHeader("Related Albums", {})
                    Spacer(Modifier.height(CardSizes.gapMd.dp))
                    CarouselRow {
                        qpAlbums.forEach { album ->
                            AlbumCard(album = album, text = text, dim = dim)
                        }
                    }
                    Spacer(Modifier.height(CardSizes.gapLg.dp))
                }

                val qpArtists = related.artists
                if (qpArtists != null && qpArtists.isNotEmpty()) {
                    SectionHeader("Similar Artists", {})
                    Spacer(Modifier.height(CardSizes.gapSm.dp))
                    CarouselRow {
                        qpArtists.forEach { artist ->
                            ArtistCard(artist = artist, text = text, dim = dim)
                        }
                    }
                    Spacer(Modifier.height(CardSizes.gapLg.dp))
                }

                val qpPlaylists = related.playlists
                if (qpPlaylists != null && qpPlaylists.isNotEmpty()) {
                    SectionHeader("Recommended Playlists", {})
                    Spacer(Modifier.height(CardSizes.gapSm.dp))
                    CarouselRow {
                        qpPlaylists.forEach { playlist ->
                            PlaylistCard(playlist = playlist, text = text, dim = dim)
                        }
                    }
                    Spacer(Modifier.height(CardSizes.gapXl.dp))
                }
            } ?: run {
                if (relatedResult == null) {
                    QuickPicksSkeleton()
                }
            }

            // discovr
            loadedPage?.let { p ->
                // mooodngenre
                if (p.moods.isNotEmpty()) {
                    SectionHeader("Moods & Genres", onMore = onMoreMoods)
                    Spacer(Modifier.height(CardSizes.gapSm.dp))
                    CarouselRow {
                        p.moods.sortedBy { it.title }.forEach { mood ->
                            val moodColor = Color(mood.stripeColor)
                            val moodTextColor = if (moodColor.luminance() >= 0.5f) Color.Black else Color.White
                            Card(
                                shape = RoundedCornerShape(Sizes.radiusLg.dp),
                                colors = CardDefaults.cardColors(containerColor = moodColor),
                                modifier = Modifier
                                    .width(CardSizes.moodW.dp)
                                    .height(CardSizes.moodH.dp)
                                    .padding(end = CardSizes.moodEndPad.dp)
                                    .clickable { }
                            ) {
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier.padding(start = CardSizes.moodInnerStart.dp)
                                ) {
                                    Text(
                                        text = mood.title,
                                        color = moodTextColor,
                                        fontSize = CardSizes.moodFont.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(CardSizes.gapLg.dp))
                }

                // new
                if (p.newReleaseAlbums.isNotEmpty()) {
                    SectionHeader("New Releases", onMore = onMoreAlbums)
                    Spacer(Modifier.height(CardSizes.gapSm.dp))
                    CarouselRow {
                        p.newReleaseAlbums.forEach { album ->
                            AlbumCard(album = album, text = text, dim = dim)
                        }
                    }
                    Spacer(Modifier.height(CardSizes.gapLg.dp))
                }

                // trendink
                if (p.trending.songs.isNotEmpty()) {
                    SectionHeader("Trending", onMore = onMoreTrending)
                    Spacer(Modifier.height(CardSizes.gapSm.dp))
                    CarouselRow {
                        p.trending.songs.forEach { songItem ->
                            val song = songItem.toSong()
                            CompactSongCard(
                                song = song,
                                text = text,
                                dim = dim,
                                onClick = { onPlaySong(song) }
                            )
                        }
                    }
                }
            } ?: run {
                MoodsSkeleton()
                Spacer(Modifier.height(CardSizes.gapXl.dp))
                NewReleasesSkeleton()
                Spacer(Modifier.height(CardSizes.gapXl.dp))
                TrendingSkeleton()
            }
        }
        val scrollbarAlpha by animateFloatAsState(
            targetValue = if (scrollState.isScrollInProgress) 1f else 0f,
            animationSpec = tween(durationMillis = 600)
        )
        VerticalScrollbar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = 8.dp, bottom = 8.dp, end = 2.dp)
                .alpha(scrollbarAlpha),
            adapter = rememberScrollbarAdapter(scrollState),
            style = sbStyle
        )
    }
    }
}

// carousel row
@Composable
private fun CarouselRow(
    modifier: Modifier = Modifier,
    bgColor: Color = Color(0xFF0a0a0a),
    content: @Composable RowScope.() -> Unit
) {
    val scrollState = rememberScrollState()

    // edge fade alpha
    val edgeFadeAlpha by animateFloatAsState(
        targetValue = if (scrollState.value > 0f) 1f else 0f,
        animationSpec = tween(durationMillis = 300)
    )
    val rightFadeAlpha by animateFloatAsState(
        targetValue = if (scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue) 0.8f else 0f,
        animationSpec = tween(durationMillis = 300)
    )

    var viewportWidth by remember { mutableStateOf(0f) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(scrollState) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        scrollState.dispatchRawDelta(-dragAmount.x)
                    }
                }
                .onSizeChanged { viewportWidth = it.width.toFloat() }
                .horizontalScroll(scrollState)
                .drawWithContent {
                    drawContent()
                    // INNER to horizontalScroll → content coords, use viewportWidth for right edge
                    val fadeW = Sizes.homeFadeWidth.dp.toPx()
                    val scrollOff = scrollState.value.toFloat()
                    val vw = if (viewportWidth > 0f) viewportWidth else size.width
                    if (edgeFadeAlpha > 0.001f) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(bgColor.copy(alpha = edgeFadeAlpha), Color.Transparent),
                                startX = scrollOff,
                                endX = scrollOff + fadeW
                            ),
                            size = Size(fadeW, size.height),
                            topLeft = Offset(scrollOff, 0f)
                        )
                    }
                    if (rightFadeAlpha > 0.001f) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, bgColor.copy(alpha = rightFadeAlpha)),
                                startX = scrollOff + vw - fadeW,
                                endX = scrollOff + vw
                            ),
                            size = Size(fadeW, size.height),
                            topLeft = Offset(scrollOff + vw - fadeW, 0f)
                        )
                    }
                }
        ) {
            Row(content = content)
        }

    }
}

@Composable
private fun SectionHeader(title: String, onMore: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            color = Color(0xFFf2f0eb),
            fontSize = CardSizes.headerTitle.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onMore) {
            Text(
                text = "More",
                color = Color(0xFFa8a39a),
                fontSize = CardSizes.headerMore.sp
            )
        }
    }
}

@Composable
private fun CompactSongCard(
    song: Song,
    text: Color,
    dim: Color,
    onClick: () -> Unit
) {
    val cardSize = CardSizes.cardW.dp
    val cardH = cardSize + CardSizes.cardTextH.dp
    HomeCard(
        cardWidth = cardSize,
        cardHeight = cardH,
        horizontalPadding = CardSizes.compactSongInnerPad.dp,
        endPad = CardSizes.cardEndPad.dp,                        thumbClipShape = RoundedCornerShape(topStart = Sizes.radiusMd.dp, topEnd = Sizes.radiusMd.dp),
        onClick = onClick,
        thumbnail = {
            val density = LocalDensity.current
            song.thumbnailUrl?.let { thumb ->
                val thumbPx = with(density) { CardSizes.cardW.dp.toPx().toInt() }
                NetworkImage(
                    url = thumb,
                    modifier = Modifier.fillMaxSize(),
                    requestedSize = thumbPx
                )
            }
        },
        title = {
            Text(
                text = song.title,
                color = text,
                fontSize = CardSizes.compactSongTitle.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        subtitle = song.artistsText?.let { author -> {
            Text(
                text = author,
                color = dim,
                fontSize = CardSizes.compactSongArt.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }}
    )
}

@Composable
private fun AlbumCard(
    album: Innertube.AlbumItem,
    text: Color,
    dim: Color
) {
    val cardSize = CardSizes.cardW.dp
    val cardH = cardSize + CardSizes.cardTextH.dp
    HomeCard(
        cardWidth = cardSize,
        cardHeight = cardH,
        horizontalPadding = CardSizes.albumInnerPad.dp,
        endPad = CardSizes.cardEndPad.dp,                        thumbClipShape = RoundedCornerShape(topStart = Sizes.radiusMd.dp, topEnd = Sizes.radiusMd.dp),
        thumbnail = {
            album.thumbnail?.let { thumb ->
                val density = LocalDensity.current
                val thumbPx = with(density) { CardSizes.cardW.dp.toPx().toInt() }
                NetworkImage(
                    url = thumb.url,
                    modifier = Modifier.fillMaxSize(),
                    requestedSize = thumbPx
                )
            }
        },
        title = {
            Text(
                text = album.info?.name ?: "Untitled",
                color = text,
                fontSize = CardSizes.albumTitle.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        subtitle = album.authors?.firstOrNull()?.let { author -> {
            Text(
                text = author.name ?: "",
                color = dim,
                fontSize = CardSizes.albumAuthor.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }}
    )
}

@Composable
private fun ArtistCard(
    artist: Innertube.ArtistItem,
    text: Color,
    dim: Color
) {
    val cardSize = CardSizes.cardW.dp
    val cardH = cardSize + CardSizes.cardTextH.dp
    HomeCard(
        cardWidth = cardSize,
        cardHeight = cardH,
        horizontalPadding = CardSizes.artistVertPad.dp,
        endPad = CardSizes.cardEndPad.dp,
        thumbClipShape = CircleShape,
        thumbnail = {
            artist.thumbnail?.let { thumb ->
                val density = LocalDensity.current
                val thumbPx = with(density) { CardSizes.cardW.dp.toPx().toInt() }
                NetworkImage(
                    url = thumb.url,
                    modifier = Modifier.fillMaxSize(),
                    requestedSize = thumbPx
                )
            }
        },
        title = {
            Text(
                text = artist.info?.name ?: "Unknown",
                color = text,
                fontSize = CardSizes.artistName.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

@Composable
private fun PlaylistCard(
    playlist: Innertube.PlaylistItem,
    text: Color,
    dim: Color
) {
    val cardSize = CardSizes.cardW.dp
    val cardH = cardSize + CardSizes.cardTextH.dp
    HomeCard(
        cardWidth = cardSize,
        cardHeight = cardH,
        horizontalPadding = CardSizes.playlistInnerPad.dp,
        endPad = CardSizes.cardEndPad.dp,                        thumbClipShape = RoundedCornerShape(topStart = Sizes.radiusMd.dp, topEnd = Sizes.radiusMd.dp),
        thumbnail = {
            playlist.thumbnail?.let { thumb ->
                val density = LocalDensity.current
                val thumbPx = with(density) { CardSizes.cardW.dp.toPx().toInt() }
                NetworkImage(
                    url = thumb.url,
                    modifier = Modifier.fillMaxSize(),
                    requestedSize = thumbPx
                )
            }
        },
        title = {
            Text(
                text = playlist.info?.name ?: "Untitled",
                color = text,
                fontSize = CardSizes.playlistName.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        subtitle = {
            Text(
                text = "${playlist.songCount ?: 0} songs",
                color = dim,
                fontSize = CardSizes.playlistCount.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}
