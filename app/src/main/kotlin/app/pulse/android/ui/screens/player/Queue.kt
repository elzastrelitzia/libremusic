package app.pulse.android.ui.screens.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import app.pulse.android.R
import app.pulse.android.preferences.AppearancePreferences
import androidx.core.content.edit
import app.pulse.android.preferences.PlayerPreferences
import app.pulse.android.service.PlayerService
import app.pulse.android.ui.components.LocalMenuState
import app.pulse.android.ui.components.themed.QueuedMediaItemMenu
import app.pulse.android.ui.components.themed.ReorderHandle
import app.pulse.android.ui.modifiers.horizontalFadingEdge
import app.pulse.android.ui.modifiers.verticalFadingEdge
import app.pulse.android.ui.items.SongItem
import app.pulse.android.ui.modifiers.swipeToClose
import app.pulse.android.utils.DisposableListener
import app.pulse.android.utils.shouldBePlaying
import app.pulse.android.utils.shuffleQueue
import app.pulse.android.utils.smoothScrollToTop
import app.pulse.android.utils.bold
import app.pulse.android.utils.windows
import app.pulse.compose.reordering.animateItemPlacement
import app.pulse.compose.reordering.draggedItem
import app.pulse.compose.reordering.rememberReorderingState
import app.pulse.core.ui.Dimensions
import app.pulse.core.ui.LocalAppearance
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueOverlay(
    binder: PlayerService.Binder,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    windowInsets: WindowInsets = WindowInsets.systemBars,
    lazyListState: LazyListState = rememberLazyListState()
) {
    val (colorPalette, typography) = LocalAppearance.current
    val menuState = LocalMenuState.current

    var mediaItemIndex by remember {
        mutableIntStateOf(if (binder.player.mediaItemCount == 0) -1 else binder.player.currentMediaItemIndex)
    }
    var windows by remember { mutableStateOf(binder.player.currentTimeline.windows) }
    var shouldBePlaying by remember { mutableStateOf(binder.player.shouldBePlaying) }
    val reorderingState = rememberReorderingState(
        lazyListState = lazyListState,
        key = windows,
        onDragEnd = binder.player::moveMediaItem
    )

    binder.player.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItemIndex = if (binder.player.mediaItemCount == 0) -1 else binder.player.currentMediaItemIndex
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                windows = timeline.windows
                mediaItemIndex = if (binder.player.mediaItemCount == 0) -1 else binder.player.currentMediaItemIndex
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                shouldBePlaying = binder.player.shouldBePlaying
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                shouldBePlaying = binder.player.shouldBePlaying
            }
        }
    }

    val isScrolling by remember { derivedStateOf { lazyListState.isScrollInProgress } }

    Column(modifier = modifier.padding(horizontal = 48.dp)) {
        // header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = stringResource(R.string.queue),
                style = typography.l.bold.copy(color = colorPalette.text)
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.shuffle_new),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(
                        if (binder.player.shuffleModeEnabled) colorPalette.accent else colorPalette.text
                    ),                        modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            reorderingState.coroutineScope.launch {
                                lazyListState.smoothScrollToTop()
                            }.invokeOnCompletion {
                                binder.player.shuffleQueue()
                            }
                        }
                )
                Image(
                    painter = painterResource(
                        when {
                            PlayerPreferences.trackLoopEnabled -> R.drawable.repeat_once
                            PlayerPreferences.queueLoopEnabled -> R.drawable.repeat_forever
                            else -> R.drawable.repeat_forever
                        }
                    ),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(
                        if (PlayerPreferences.trackLoopEnabled || PlayerPreferences.queueLoopEnabled) colorPalette.accent else colorPalette.text
                    ),                        modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            if (PlayerPreferences.queueLoopEnabled) {
                                PlayerPreferences.edit(commit = true) {
                                    putBoolean("trackLoopEnabled", true)
                                    putBoolean("queueLoopEnabled", false)
                                }
                            } else if (PlayerPreferences.trackLoopEnabled) {
                                PlayerPreferences.edit(commit = true) {
                                    putBoolean("trackLoopEnabled", false)
                                }
                            } else {
                                PlayerPreferences.edit(commit = true) {
                                    putBoolean("queueLoopEnabled", true)
                                }
                            }
                        }
                )
                Image(
                    painter = painterResource(R.drawable.infinite),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(
                        if (PlayerPreferences.crossfadeSeconds > 0) colorPalette.accent else colorPalette.text
                    ),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            PlayerPreferences.crossfadeSeconds = if (PlayerPreferences.crossfadeSeconds > 0) 0 else 6
                        }
                )
            }
        }

        // Song list
        LookaheadScope {
            LazyColumn(
                state = lazyListState,
                contentPadding = WindowInsets.systemBars
                    .only(WindowInsetsSides.Horizontal)
                    .asPaddingValues().let { hPadding ->
                        PaddingValues(
                            start = hPadding.calculateStartPadding(LocalLayoutDirection.current),
                            end = hPadding.calculateEndPadding(LocalLayoutDirection.current),
                            top = 4.dp,
                            bottom = 4.dp
                        )
                    },
                modifier = Modifier
                    .verticalFadingEdge(topSize = 24, bottomSize = 24) // higher = smaller
                    .horizontalFadingEdge(leftSize = 24, rightSize = 20)
                    .weight(1f)
            ) {
                itemsIndexed(
                    items = windows,
                    key = { _, window -> window.uid.hashCode() },
                    contentType = { _, _ -> "song" }
                ) { i, window ->
                    val isPlayingThisMediaItem = mediaItemIndex == window.firstPeriodIndex

                    SongItem(
                        song = window.mediaItem,
                        thumbnailSize = Dimensions.thumbnails.song,
                        isPlaying = shouldBePlaying && isPlayingThisMediaItem,
                        trailingContent = {
                            ReorderHandle(
                                reorderingState = reorderingState,
                                index = i
                            )
                        },
                        modifier = Modifier
                            .combinedClickable(
                                onLongClick = {
                                    menuState.display {
                                        QueuedMediaItemMenu(
                                            mediaItem = window.mediaItem,
                                            indexInQueue = if (isPlayingThisMediaItem) null else window.firstPeriodIndex,
                                            onDismiss = menuState::hide
                                        )
                                    }
                                },
                                onClick = {
                                    if (isPlayingThisMediaItem) {
                                        if (shouldBePlaying) binder.player.pause() else binder.player.play()
                                    } else {
                                        binder.player.seekToDefaultPosition(window.firstPeriodIndex)
                                        binder.player.playWhenReady = true
                                    }
                                }
                            )
                            .let { mod ->
                                if (!isScrolling) mod
                                    .animateItemPlacement(reorderingState)
                                    .draggedItem(
                                        reorderingState = reorderingState,
                                        index = i,
                                        draggedElevation = 0.dp
                                    )
                                else mod
                            }
                            .let {
                                if (!isPlayingThisMediaItem && !isScrolling)
                                    it.swipeToClose(
                                        key = windows,
                                        delay = 100.milliseconds,
                                        requireUnconsumed = true
                                    ) {
                                        binder.player.removeMediaItem(window.firstPeriodIndex)
                                    }
                                else it
                            },
                        clip = !reorderingState.isDragging,
                        hideExplicit = !isPlayingThisMediaItem && AppearancePreferences.hideExplicit
                    )
                }
            }
        }
    }
}
