package app.pulse.android.ui.screens.player

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.pulse.android.Database
import app.pulse.android.LocalPlayerServiceBinder
import app.pulse.android.R
import app.pulse.android.models.ui.toUiMedia
import app.pulse.android.preferences.PlayerPreferences
import app.pulse.android.query
import app.pulse.android.service.PlayerService
import app.pulse.android.transaction
import app.pulse.android.ui.components.BottomSheet
import app.pulse.android.ui.components.BottomSheetState
import app.pulse.android.ui.components.LocalMenuState
import app.pulse.android.ui.components.themed.BaseMediaItemMenu
import app.pulse.android.ui.components.themed.SecondaryTextButton
import app.pulse.android.ui.components.themed.SliderDialog
import app.pulse.android.ui.components.themed.SliderDialogBody
import app.pulse.android.utils.DisposableListener
import app.pulse.android.utils.LocalDownloads
import app.pulse.android.utils.asMediaItem
import app.pulse.android.utils.positionAndDurationState
import app.pulse.android.utils.rememberEqualizerLauncher
import app.pulse.android.utils.rememberPipHandler
import app.pulse.android.utils.seamlessPlay
import app.pulse.android.utils.rememberIsBuffering
import app.pulse.android.utils.shouldBePlaying
import app.pulse.compose.persist.PersistMapCleanup
import app.pulse.compose.routing.OnGlobalRoute
import app.pulse.core.ui.LocalAppearance
import app.pulse.core.ui.utils.roundedShape
import app.pulse.core.data.utils.songBundle
import app.pulse.providers.innertube.models.NavigationEndpoint
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun Player(
    layoutState: BottomSheetState,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(
        topStart = 12.dp,
        topEnd = 12.dp
    ),
    windowInsets: WindowInsets = WindowInsets.systemBars
) = with(PlayerPreferences) {
    val menuState = LocalMenuState.current
    val (colorPalette, _, _) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current

    val pipHandler = rememberPipHandler()

    PersistMapCleanup(prefix = "queue/suggestions")

    var mediaItem by remember(binder) {
        mutableStateOf(
            value = binder?.player?.currentMediaItem,
            policy = neverEqualPolicy()
        )
    }

    // The service flips mediaItemState to the incoming song at crossfade
    // start; collect it so the screen follows the fade, not only the audible
    // player's boundary transition. (The listener below re-sets the same
    // value at the boundary harmless, neverEqualPolicy forces a redraw.)
    LaunchedEffect(binder) {
        binder?.mediaItemState?.collect { mediaItem = it }
    }
    var shouldBePlaying by remember(binder) { mutableStateOf(binder?.player?.shouldBePlaying == true) }
    val isBuffering = binder?.player.rememberIsBuffering()

    var historyMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    LaunchedEffect(binder, mediaItem) {
        if (mediaItem == null) {
            Database.history(1).collect { songs ->
                historyMediaItem = songs.firstOrNull()?.asMediaItem
            }
        } else {
            historyMediaItem = null
        }
    }

    var likedAt by remember(mediaItem, historyMediaItem) {
        val activeMediaItem = mediaItem ?: historyMediaItem
        mutableStateOf(
            value = null,
            policy = object : SnapshotMutationPolicy<Long?> {
                override fun equivalent(a: Long?, b: Long?): Boolean {
                    mediaItem?.mediaId?.let {
                        query {
                            Database.like(it, b)
                        }
                    }
                    return a == b
                }
            }
        )
    }

    LaunchedEffect(mediaItem, historyMediaItem) {
        val activeMediaId = mediaItem?.mediaId ?: historyMediaItem?.mediaId
        if (activeMediaId != null) {
            Database
                .likedAt(activeMediaId)
                .distinctUntilChanged()
                .collect { likedAt = it }
        }
    }

    binder?.player.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(newMediaItem: MediaItem?, reason: Int) {
                mediaItem = newMediaItem
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                shouldBePlaying = player.shouldBePlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                shouldBePlaying = player.shouldBePlaying
            }
        }
    }

    val (position, duration) = binder?.player.positionAndDurationState()
    val activeMediaItem = mediaItem ?: historyMediaItem
    val metadata = remember(activeMediaItem) { activeMediaItem?.mediaMetadata }
    val extras = remember(metadata) { metadata?.extras?.songBundle }

    val horizontalBottomPaddingValues = windowInsets
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        .asPaddingValues()

    OnGlobalRoute { if (layoutState.expanded) layoutState.collapseSoft() }

    val dismissAction = {
        if (mediaItem != null) {
            binder?.let { onDismiss(it) }
        }
        layoutState.collapseSoft()
    }

    BottomSheet(
        state = layoutState,
        modifier = modifier.fillMaxSize(),
        onDismiss = dismissAction,
        backHandlerEnabled = !menuState.isDisplayed,
        dragEnabled = false,
        collapsedContent = { },
    ) {
        var isShowingStatsForNerds by rememberSaveable { mutableStateOf(false) }
        var isShowingLyricsDialog by rememberSaveable { mutableStateOf(false) }
        var overlayMode by rememberSaveable { mutableStateOf(OverlayMode.None) }
        val isShowingLyrics = overlayMode == OverlayMode.Lyrics
        val isShowingQueue = overlayMode == OverlayMode.Queue

        if (isShowingLyricsDialog) LyricsDialog(onDismiss = { isShowingLyricsDialog = false })

        val containerModifier = Modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    0.5f to colorPalette.background1,
                    1f to colorPalette.background0
                )
            )

        var audioDialogOpen by rememberSaveable { mutableStateOf(false) }
        var boostDialogOpen by rememberSaveable { mutableStateOf(false) }

        NewLayoutContent(
            mediaItem = activeMediaItem,
            binder = binder,
            likedAt = likedAt,
            setLikedAt = { likedAt = it },
            position = position,
            duration = duration,
            onLyricsClick = {
                overlayMode = if (overlayMode == OverlayMode.Lyrics) OverlayMode.None else OverlayMode.Lyrics
            },
            onQueueClick = {
                overlayMode = if (overlayMode == OverlayMode.Queue) OverlayMode.None else OverlayMode.Queue
            },
            onMenuLaunch = {
                mediaItem?.let {
                    menuState.display {
                        PlayerMenu(
                            onDismiss = menuState::hide,
                            mediaItem = it,
                            binder = binder!!,
                            onShowSpeedDialog = { audioDialogOpen = true },
                            onShowNormalizationDialog = {
                                boostDialogOpen = true
                            }.takeIf { volumeNormalization },
                            likedAt = likedAt
                        )
                    }
                }
            },
            onDrag = { layoutState.dispatchRawDelta(it) },
            onDragEnd = { layoutState.fling(it, dismissAction) },
            isShowingLyrics = isShowingLyrics,
            onShowLyrics = { overlayMode = if (it) OverlayMode.Lyrics else OverlayMode.None },
            isShowingQueue = isShowingQueue,
            onShowQueue = { overlayMode = if (it) OverlayMode.Queue else OverlayMode.None },
            modifier = containerModifier
        )

        if (audioDialogOpen) SliderDialog(
            onDismiss = { audioDialogOpen = false },
            title = stringResource(R.string.playback_settings)
        ) {
            SliderDialogBody(
                provideState = { remember(speed) { mutableFloatStateOf(speed) } },
                onSlideComplete = { speed = it },
                min = 0f,
                max = 2f,
                toDisplay = {
                    if (it <= 0.01f) stringResource(R.string.minimum_speed_value)
                    else stringResource(R.string.format_multiplier, "%.2f".format(it))
                },
                steps = 39,
                label = stringResource(R.string.playback_speed)
            )
            SliderDialogBody(
                provideState = { remember(pitch) { mutableFloatStateOf(pitch) } },
                onSlideComplete = { pitch = it },
                min = 0f,
                max = 2f,
                toDisplay = {
                    if (it <= 0.01f) stringResource(R.string.minimum_speed_value)
                    else stringResource(R.string.format_multiplier, "%.2f".format(it))
                },
                steps = 39,
                label = stringResource(R.string.playback_pitch)
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                SecondaryTextButton(
                    text = stringResource(R.string.reset),
                    onClick = {
                        speed = 1f
                        pitch = 1f
                    }
                )
            }
        }

        if (boostDialogOpen) {
            fun submit(state: Float) = transaction {
                mediaItem?.mediaId?.let { mediaId ->
                    Database.setLoudnessBoost(
                        songId = mediaId,
                        loudnessBoost = state.takeUnless { it == 0f }
                    )
                }
            }

            SliderDialog(
                onDismiss = { boostDialogOpen = false },
                title = stringResource(R.string.volume_boost)
            ) {
                SliderDialogBody(
                    provideState = {
                        val state = remember { mutableFloatStateOf(0f) }

                        LaunchedEffect(mediaItem) {
                            mediaItem?.mediaId?.let { mediaId ->
                                Database
                                    .loudnessBoost(mediaId)
                                    .distinctUntilChanged()
                                    .collect { state.floatValue = it ?: 0f }
                            }
                        }

                        state
                    },
                    onSlideComplete = { submit(it) },
                    min = -20f,
                    max = 20f,
                    toDisplay = { stringResource(R.string.format_db, "%.2f".format(it)) }
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    SecondaryTextButton(
                        text = stringResource(R.string.reset),
                        onClick = { submit(0f) }
                    )
                }
            }
        }


    }
}

@Composable
@OptIn(UnstableApi::class)
private fun PlayerMenu(
    binder: PlayerService.Binder,
    mediaItem: MediaItem,
    onDismiss: () -> Unit,
    onShowSpeedDialog: (() -> Unit)? = null,
    onShowNormalizationDialog: (() -> Unit)? = null,
    likedAt: Long?
) {
    val launchEqualizer by rememberEqualizerLauncher(audioSessionId = { binder.player.audioSessionId })
    val context = LocalContext.current

    // API 24-29: runtime WRITE_EXTERNAL_STORAGE permission launcher
    val writePermLauncher = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted ->
                if (granted && LocalDownloads.canWrite(context)) {
                    LocalDownloads.save(context, mediaItem, likedAt)
                }
            }
        )
    } else null

    BaseMediaItemMenu(
        mediaItem = mediaItem,
        onStartRadio = {
            binder.stopRadio()
            binder.player.seamlessPlay(mediaItem)
            binder.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
        },
        onGoToEqualizer = launchEqualizer,
        onShowSleepTimer = {},
        onDismiss = onDismiss,
        onShowSpeedDialog = onShowSpeedDialog,
        onShowNormalizationDialog = onShowNormalizationDialog,
        onSaveToLocal = {
            if (LocalDownloads.canWrite(context)) {
                LocalDownloads.save(context, mediaItem, likedAt)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                .apply { data = Uri.fromParts("package", context.packageName, null) }
                        )
                    }.onFailure {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                } else {
                    writePermLauncher?.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    )
}

private fun onDismiss(binder: PlayerService.Binder) {
    binder.stopRadio()
    binder.player.clearMediaItems()
}

private enum class OverlayMode { None, Lyrics, Queue }
