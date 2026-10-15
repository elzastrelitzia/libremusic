package app.pulse.android.ui.screens.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.util.Locale
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.pulse.android.R
import app.pulse.android.models.ui.UiMedia
import app.pulse.android.models.ui.toUiMedia
import app.pulse.android.preferences.LyricsTranslationLanguage
import app.pulse.android.preferences.PlayerPreferences
import app.pulse.android.service.PlayerService
import app.pulse.android.ui.components.SeekBar
import app.pulse.android.utils.bold
import app.pulse.android.utils.isMlKitSupportedLang
import app.pulse.android.utils.medium
import app.pulse.android.utils.forceSeekToNext
import app.pulse.android.utils.forceSeekToPrevious
import app.pulse.android.utils.semiBold
import app.pulse.android.utils.shouldBePlaying
import app.pulse.android.utils.rememberIsBuffering
import app.pulse.android.utils.thumbnail
import app.pulse.core.ui.Dimensions
import app.pulse.core.ui.LocalAppearance
import app.pulse.core.ui.utils.px
import app.pulse.core.ui.utils.roundedShape
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.allowHardware
import coil3.toBitmap
import com.skydoves.cloudy.cloudy
import app.pulse.android.preferences.AppearancePreferences
import app.pulse.core.ui.ColorPalette
import app.pulse.core.ui.colorPaletteOf
import app.pulse.android.Database
import app.pulse.android.service.LOCAL_KEY_PREFIX
import app.pulse.android.transaction
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

@Composable
fun NewLayoutContent(
    mediaItem: MediaItem?,
    binder: PlayerService.Binder?,
    likedAt: Long?,
    setLikedAt: (Long?) -> Unit,
    position: Long,
    duration: Long,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
    onMenuLaunch: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    isShowingLyrics: Boolean,
    onShowLyrics: (Boolean) -> Unit,
    isShowingQueue: Boolean = false,
    onShowQueue: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val (baseColorPalette, typography, thumbnailCornerSize) = LocalAppearance.current
    val context = LocalContext.current
    val player = binder?.player ?: return
    val shouldBePlaying = player.shouldBePlaying
    val isBuffering = player.rememberIsBuffering() ||
        (shouldBePlaying && player.playbackState != Player.STATE_READY)
    val metadata = remember(mediaItem) { mediaItem?.mediaMetadata }
    val mediaId = mediaItem?.mediaId ?: return
    val artworkUri = remember(mediaId) {
        val thumbSize = with(context.resources.displayMetrics) {
            maxOf(widthPixels, heightPixels)
        }
        mediaItem.mediaMetadata.artworkUri?.thumbnail(thumbSize)
    }
    var localColorPalette by remember(mediaId) { mutableStateOf<ColorPalette?>(null) }
    val dynamicSource = AppearancePreferences.colorSource
    val darkMode = AppearancePreferences.darkness
    val isDark = baseColorPalette.isDark

    LaunchedEffect(mediaId, artworkUri) {
        if (artworkUri == null) {
            localColorPalette = null
            return@LaunchedEffect
        }
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(artworkUri)
                .allowHardware(false)
                .build()
        )
        if (result is coil3.request.SuccessResult) {
            localColorPalette = colorPaletteOf(
                source = dynamicSource,
                darkness = darkMode,
                isDark = isDark,
                materialAccentColor = null,
                sampleBitmap = result.image.toBitmap()
            )
        }
    }

    val colorPalette = localColorPalette ?: baseColorPalette

    val uiMedia = remember(mediaId, duration) { mediaItem.toUiMedia(duration) }

    var lyricsReady by remember(mediaId) { mutableStateOf(LyricsCache[mediaId] != null) }
    // seeded from the persisted toggle so translation survives song changes
    // auto-disable (same language / failure) only affects the current song
    var showTranslation by remember(mediaId) {
        mutableStateOf(PlayerPreferences.lyricsTranslationEnabled)
    }
    var translationBusy by remember(mediaId) { mutableStateOf(false) }
    var showingTranslatorDialog by remember { mutableStateOf(false) }

    if (showingTranslatorDialog) TranslationLanguageDialog(
        translationActive = showTranslation,
        onDismiss = { showingTranslatorDialog = false },
        onSelectLanguage = {
            PlayerPreferences.lyricsTranslationLanguage = it
            PlayerPreferences.lyricsTranslationEnabled = true
            showTranslation = true
            showingTranslatorDialog = false
        },
        onHide = {
            PlayerPreferences.lyricsTranslationEnabled = false
            showTranslation = false
            showingTranslatorDialog = false
        }
    )

    LaunchedEffect(mediaId) {
        if (LyricsCache[mediaId] != null) {
            lyricsReady = true
            return@LaunchedEffect
        }
        val existing = Database.lyrics(mediaId).first()
        if (existing?.fixed != null && existing?.synced != null) {
            LyricsCache[mediaId] = existing
            lyricsReady = true
            return@LaunchedEffect
        }

        val meta = mediaItem?.mediaMetadata ?: return@LaunchedEffect
        val artist = meta.artist?.toString().orEmpty()
        val title = (meta.title?.toString().orEmpty()).let {
            if (mediaId.startsWith(LOCAL_KEY_PREFIX)) it.substringBeforeLast('.').trim()
            else it
        }
        val album = meta.albumTitle?.toString()

        val (fixed, synced) = fetchLyricsParallel(
            mediaId = mediaId,
            artist = artist,
            title = title,
            album = album,
            duration = duration,
            currentFixed = null,
            currentSynced = null
        )

        lyricsReady = (fixed != null || synced != null)
        if (lyricsReady) {
            val lyrics = app.pulse.android.models.Lyrics(
                songId = mediaId,
                fixed = fixed.orEmpty(),
                synced = synced.orEmpty()
            )
            LyricsCache[mediaId] = lyrics
            transaction {
                runCatching { Database.upsert(lyrics) }
            }
        }
    }

    // ponytail: theme colors over artwork luminance detection
    val titleTextColor = colorPalette.text
    val authorTextColor = colorPalette.textSecondary

    val gradientEndColor = remember(colorPalette) {
        colorPalette.background0.copy(alpha = 0.95f)
    }



    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (artworkUri != null) {
                key(artworkUri, isShowingLyrics || isShowingQueue) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(artworkUri)
                            .memoryCacheKey(artworkUri.toString())
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()

                            .cloudy(
                                radius = 64.dp.px,
                                enabled = isShowingLyrics || isShowingQueue
                            )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.25f to Color.Transparent,
                            0.45f to Color.Transparent,
                            0.7f to gradientEndColor,
                            1f to gradientEndColor
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (isShowingLyrics || isShowingQueue) 0.9f else 0f
                    }
                    .background(colorPalette.background0)
            )

        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    WindowInsets.systemBars
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        .asPaddingValues()
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .pointerInput(Unit) {
                        val velocityTracker = VelocityTracker()
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                velocityTracker.addPointerInputChange(change)
                                onDrag(dragAmount)
                            },
                            onDragEnd = {
                                val velocity = -velocityTracker.calculateVelocity().y
                                velocityTracker.resetTracking()
                                onDragEnd(velocity)
                            },
                            onDragCancel = {
                                velocityTracker.resetTracking()
                                onDragEnd(0f)
                            }
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(
                            color = colorPalette.text.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .align(Alignment.TopCenter)
                )
            }

            val lyricsContentAlpha by animateFloatAsState(
                targetValue = if (isShowingLyrics || isShowingQueue) 1f else 0f,
                label = "lyricsContentAlpha"
            )

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(lyricsContentAlpha)
                        .padding(horizontal = 48.dp)
                        .padding(top = 32.dp, bottom = 8.dp)
                ) {
                        if (artworkUri != null) {
                            AsyncImage(
                                model = artworkUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(thumbnailCornerSize.roundedShape)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            BasicText(
                                text = metadata?.title?.toString().orEmpty(),
                                style = typography.s.bold.copy(color = titleTextColor),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            BasicText(
                                text = metadata?.artist?.toString().orEmpty(),
                                style = typography.xs.semiBold.copy(color = authorTextColor),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        if (isShowingLyrics) {
                            if (translationBusy) app.pulse.android.ui.components.themed.CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = colorPalette.accent
                            ) else Image(
                                painter = painterResource(R.drawable.translate),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(
                                    if (showTranslation) colorPalette.accent else colorPalette.text
                                ),
                                modifier = Modifier
                                    .clickable { showingTranslatorDialog = true }
                                    .size(24.dp)
                            )
                        } else Image(
                            painter = if (likedAt == null) painterResource(R.drawable.heart_outline)
                            else painterResource(R.drawable.heart),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorPalette.text),
                            modifier = Modifier
                                .clickable(enabled = isShowingQueue) {
                                    setLikedAt(
                                        if (likedAt == null) System.currentTimeMillis() else null
                                    )
                                }
                                .size(24.dp)
                        )
                }

                // Hoisted above the AnimatedVisibility so the lyric scroll
                // position survives close/reopen, otherwise the list resets
                // to the top and the follow-effect visibly re-centers on every
                // open (the vertical bounce).
                val lyricsListState = rememberLazyListState()
                val queueListState = rememberLazyListState()
                var hasScrolledQueueToCurrent by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isShowingLyrics || isShowingQueue) 16.dp else 0.dp)
                        .weight(1f)
                ) {
                    if (isShowingLyrics) Lyrics(
                        mediaId = mediaId,
                        isDisplayed = true,
                        onDismiss = { onShowLyrics(false) },
                        mediaMetadataProvider = { mediaItem!!.mediaMetadata },
                        durationProvider = { player.duration },
                        ensureSongInserted = { app.pulse.android.Database.insert(mediaItem!!) },
                        modifier = Modifier.fillMaxSize(),
                        shouldShowTranslation = showTranslation,
                        setShouldShowTranslation = { showTranslation = it },
                        setTranslationBusy = { translationBusy = it },
                        showControls = false,
                        lazyListState = lyricsListState
                    )

                    if (isShowingQueue && binder != null) {
                        // Scroll to current song only on first open, then restore saved position
                        LaunchedEffect(player.currentMediaItemIndex) {
                            if (!hasScrolledQueueToCurrent && player.currentMediaItemIndex >= 0) {
                                queueListState.scrollToItem(player.currentMediaItemIndex)
                                hasScrolledQueueToCurrent = true
                            }
                        }
                        QueueOverlay(
                            binder = binder,
                            modifier = Modifier.fillMaxSize(),
                            onDismiss = { onShowQueue(false) },
                            lazyListState = queueListState
                        )
                    }
                }

                if (!isShowingLyrics && !isShowingQueue) Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                        .padding(top = 8.dp, bottom = 16.dp)
                ) {
                        Column(modifier = Modifier.weight(1f)) {
                            BasicText(
                                text = metadata?.title?.toString().orEmpty(),
                                style = typography.l.bold.copy(color = titleTextColor),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            BasicText(
                                text = metadata?.artist?.toString().orEmpty(),
                                style = typography.s.semiBold.copy(color = authorTextColor),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Image(
                            painter = if (likedAt == null) painterResource(R.drawable.heart_outline)
                            else painterResource(R.drawable.heart),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorPalette.text),
                            modifier = Modifier
                                .clickable {
                                    setLikedAt(
                                        if (likedAt == null) System.currentTimeMillis() else null
                                    )
                                }
                                .size(24.dp)
                        )
                }

                if (uiMedia != null) {
                    Box(modifier = Modifier.padding(horizontal = 48.dp)) {
                        SeekBar(
                            binder = binder,
                            position = position,
                            media = uiMedia,
                            alwaysShowDuration = true,
                            color = colorPalette.accent,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = 4.dp)
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    Image(
                        painter = painterResource(R.drawable.play_skip_back),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.accent),
                        modifier = Modifier
                            .clickable { player.forceSeekToPrevious() }
                            .size(38.dp)
                    )

                    Spacer(modifier = Modifier.width(48.dp))

                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clickable {
                                if (shouldBePlaying) player.pause()
                                else {
                                    if (player.playbackState == Player.STATE_IDLE) player.prepare()
                                    player.play()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBuffering && shouldBePlaying) {
                            app.pulse.android.ui.components.themed.CircularProgressIndicator(
                                modifier = Modifier.size(44.dp),
                                color = colorPalette.accent
                            )
                        } else {
                            Image(
                                painter = if (shouldBePlaying) painterResource(R.drawable.pause)
                                else painterResource(R.drawable.play),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(colorPalette.accent),
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(48.dp))

                    Image(
                        painter = painterResource(R.drawable.play_skip_forward),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.accent),
                        modifier = Modifier
                            .clickable { player.forceSeekToNext() }
                            .size(38.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(162.dp))
            }

            val navBottomPadding = WindowInsets.navigationBars
                .only(WindowInsetsSides.Bottom)
                .asPaddingValues()
                .calculateBottomPadding()
                .coerceAtLeast(32.dp)

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = navBottomPadding)
            ) {
                Spacer(modifier = Modifier.height(28.dp))

                NewLayoutVolumeSlider(
                    context = context,
                    colorPalette = colorPalette,
                    modifier = Modifier
                        .padding(horizontal = 48.dp)
                        .height(24.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp).padding(top = 28.dp, bottom = 16.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.lyrics),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(
                            if (lyricsReady) colorPalette.accent
                            else colorPalette.accent.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .clickable(enabled = lyricsReady, onClick = onLyricsClick)
                            .size(24.dp)
                    )

                    Image(
                        painter = painterResource(R.drawable.list),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.accent),
                        modifier = Modifier
                            .clickable(onClick = onQueueClick)
                            .size(24.dp)
                    )

                    Image(
                        painter = painterResource(R.drawable.ellipsis_horizontal),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.accent),
                        modifier = Modifier
                            .clickable(onClick = onMenuLaunch)
                            .size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NewLayoutVolumeSlider(
    context: Context,
    colorPalette: app.pulse.core.ui.ColorPalette,
    modifier: Modifier = Modifier
) {
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    var volume by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        )
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            }
        }
        context.registerReceiver(receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        onDispose { context.unregisterReceiver(receiver) }
    }

    var isDragging by remember { mutableStateOf(false) }
    val barHeight = 5.dp
    val fraction = volume / maxVolume

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Image(
            painter = painterResource(R.drawable.volume_muted),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette.accent.copy(alpha = 0.6f)),
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(barHeight * 2)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val newFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val newVol = (newFraction * maxVolume).roundToInt()
                        volume = newVol.toFloat()
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onHorizontalDrag = { _, delta ->
                            val newFraction = ((volume / maxVolume) + delta / size.width).coerceIn(0f, 1f)
                            val newVol = (newFraction * maxVolume).roundToInt()
                            volume = newVol.toFloat()
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                }
                .drawBehind {
                    val barHeightPx = barHeight.toPx()
                    val trackY = (size.height - barHeightPx) / 2f
                    val fractionVal = fraction.coerceIn(0f, 1f)
                    val thumbX = fractionVal * size.width

                    drawRoundRect(
                        color = colorPalette.background2,
                        topLeft = Offset(0f, trackY),
                        size = Size(size.width, barHeightPx),
                        cornerRadius = CornerRadius(barHeightPx / 2f)
                    )

                    drawRoundRect(
                        color = colorPalette.accent,
                        topLeft = Offset(0f, trackY),
                        size = Size(thumbX, barHeightPx),
                        cornerRadius = CornerRadius(barHeightPx / 2f)
                    )

                    if (isDragging) {
                        val thumbWidth = 8.dp.toPx()
                        val thumbHeight = 16.dp.toPx()
                        drawRoundRect(
                            color = colorPalette.onAccent,
                            topLeft = Offset(thumbX - thumbWidth / 2f, (size.height - thumbHeight) / 2f),
                            size = Size(thumbWidth, thumbHeight),
                            cornerRadius = CornerRadius(thumbHeight / 2f)
                        )
                    }
                }
        )

        Spacer(modifier = Modifier.width(8.dp))

        Image(
            painter = painterResource(R.drawable.volume_up),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette.accent.copy(alpha = 0.6f)),
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(20.dp))
    }
}

@Composable
private fun TranslationLanguageDialog(
    translationActive: Boolean,
    onDismiss: () -> Unit,
    onSelectLanguage: (LyricsTranslationLanguage) -> Unit,
    onHide: () -> Unit
) {
    val (colorPalette, typography) = LocalAppearance.current
    val selected = PlayerPreferences.lyricsTranslationLanguage
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight(0.5f)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(40.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(colorPalette.background1)
                ) {
                val unsupported = LyricsTranslationLanguage.entries.firstOrNull {
                    it != LyricsTranslationLanguage.Device && !isMlKitSupportedLang(it.langCode)
                }
                if (unsupported != null) {
                    BasicText(
                        text = stringResource(R.string.lyrics_translation_unsupported_languages),
                        style = typography.xs.semiBold.copy(color = colorPalette.textSecondary),
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(Modifier.height(72.dp))

                        val langLabel: (LyricsTranslationLanguage) -> String = { lang ->
                            if (lang == LyricsTranslationLanguage.Device) context.getString(R.string.device_language)
                            else Locale.forLanguageTag(lang.langCode).getDisplayLanguage(Locale.getDefault())
                        }
                        val filteredLanguages = LyricsTranslationLanguage.entries.filter { lang ->
                            if (lang != LyricsTranslationLanguage.Device && !isMlKitSupportedLang(lang.langCode)) return@filter false
                            query.isBlank() || langLabel(lang).contains(query.trim(), ignoreCase = true)
                        }

                        filteredLanguages.forEach { lang ->
                            val isCurrent = translationActive && lang == selected
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // re-tapping the active language turns translation off
                                    .clickable { if (isCurrent) onHide() else onSelectLanguage(lang) }
                                    .padding(vertical = 14.dp, horizontal = 16.dp)
                            ) {
                                BasicText(
                                    text = langLabel(lang),
                                    style = typography.m.semiBold.copy(
                                        color = if (isCurrent) colorPalette.accent else colorPalette.text
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrent) BasicText(
                                    text = "\u2713",
                                    style = typography.m.bold.copy(color = colorPalette.accent)
                                )
                            }
                        }

                        // end padding
                        Spacer(Modifier.height(24.dp))
                    }

                    // top fade
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(96.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        colorPalette.background1,
                                        colorPalette.background1.copy(alpha = 0.8f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // bottom
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        colorPalette.background1
                                    )
                                )
                            )
                    )

                    // search pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(percent = 50))
                            .background(colorPalette.background1)
                            .border(
                                width = 0.5.dp,
                                color = colorPalette.textSecondary.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(percent = 50)
                            )
                            .padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            textStyle = typography.l.medium,
                            singleLine = true,
                            maxLines = 1,
                            cursorBrush = SolidColor(colorPalette.text.copy(alpha = 0.4f)),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (query.isEmpty()) BasicText(
                                        text = stringResource(R.string.search_placeholder),
                                        maxLines = 1,
                                        style = typography.l.medium.copy(
                                            color = colorPalette.textSecondary.copy(alpha = 0.6f)
                                        )
                                    )
                                    innerTextField()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        if (query.isNotEmpty()) Image(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorPalette.textSecondary),
                            modifier = Modifier
                                .clickable { query = "" }
                                .size(16.dp)
                        )
                    }
                }
                }
            }
        }
    }
}
