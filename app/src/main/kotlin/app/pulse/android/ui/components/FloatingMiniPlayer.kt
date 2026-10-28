package app.pulse.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.pulse.android.Database
import app.pulse.android.LocalPlayerServiceBinder
import app.pulse.android.R
import app.pulse.android.preferences.AppearancePreferences
import app.pulse.android.models.ui.toUiMedia
import app.pulse.android.ui.components.themed.CircularProgressIndicator
import app.pulse.core.ui.utils.px
import app.pulse.android.utils.DisposableListener
import app.pulse.android.utils.asMediaItem
import app.pulse.android.utils.positionAndDurationState
import app.pulse.android.utils.rememberIsBuffering
import app.pulse.android.utils.seamlessPlay
import app.pulse.android.utils.secondary
import app.pulse.android.utils.semiBold
import app.pulse.android.utils.shouldBePlaying
import app.pulse.android.utils.thumbnail
import app.pulse.core.ui.Dimensions
import app.pulse.core.ui.LocalAppearance
import app.pulse.android.models.ui.UiMedia
import coil3.compose.AsyncImage

@Composable
fun rememberMiniPlayerState(): MiniPlayerState {
    val binder = LocalPlayerServiceBinder.current

    var mediaItem by remember(binder) {
        mutableStateOf(
            value = binder?.player?.currentMediaItem,
            policy = neverEqualPolicy()
        )
    }
    // Follow the service's song flow so the dock flips to the incoming song
    // at crossfade start, not only at the boundary transition.
    LaunchedEffect(binder) {
        binder?.mediaItemState?.collect { mediaItem = it }
    }
    var shouldBePlaying by remember(binder) { mutableStateOf(binder?.player?.shouldBePlaying == true) }
    val isBuffering = binder?.player?.rememberIsBuffering() ?: false

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

    binder?.player?.DisposableListener {
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

    val positionState = binder?.player?.positionAndDurationState()
    val duration = positionState?.component2() ?: 0L

    val activeMediaItem = mediaItem ?: historyMediaItem
    val metadata = activeMediaItem?.toUiMedia(duration)

    return remember(activeMediaItem, metadata, shouldBePlaying, isBuffering, binder, mediaItem, historyMediaItem) {
        MiniPlayerState(activeMediaItem, metadata, shouldBePlaying, isBuffering, binder, mediaItem, historyMediaItem)
    }
}

data class MiniPlayerState(
    val activeMediaItem: MediaItem?,
    val metadata: UiMedia?,
    val shouldBePlaying: Boolean,
    val isBuffering: Boolean,
    val binder: app.pulse.android.service.PlayerService.Binder?,
    val mediaItem: MediaItem?,
    val historyMediaItem: MediaItem?
)



@Composable
fun MorphingMiniPlayer(
    progress: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    contentWidth: Dp
) {
    val (colorPalette, typography) = LocalAppearance.current
    val state = rememberMiniPlayerState()
    val (activeMediaItem, metadata, shouldBePlaying, isBuffering, binder) = state

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(colorPalette.background1)
            .clickable(
                enabled = activeMediaItem != null,
                onClick = onClick
            ),
        // the pill masks from the right only, so the artwork thumb
        // stays visible as the pill narrows.
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            // the pill's clip masks the content as it narrows
            // instead of re-laying the text out (the source of the bounce).
            modifier = Modifier
                .width(contentWidth)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 0 at both rest states, 1 mid-morph: tuck/twist peaks in the middle.
            val pulse = 4f * progress.coerceIn(0f, 1f) * (1f - progress.coerceIn(0f, 1f))
            val textScale = 1f - 0.08f * pulse
            val thumbSize = (if (AppearancePreferences.compactDock) Dimensions.items.collapsedPlayerHeight else 48.dp) * (1f - 0.45f * progress)

            activeMediaItem?.mediaMetadata?.artworkUri?.thumbnail(Dimensions.thumbnails.song.px)?.let { art ->
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(thumbSize)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(colorPalette.background0)
                        .graphicsLayer {
                            rotationZ = -8f * pulse
                        }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            val density = LocalDensity.current
            val artistLineHeight = with(density) { (typography.xs.fontSize * 1.4f).toDp() }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Scale, don't re-measure: font-size animation re-rasterizes per
                // frame and snaps. Full size at both rest states.

                BasicText(
                    text = metadata?.title ?: stringResource(R.string.no_music_played),
                    style = typography.xs.semiBold.copy(
                        color = colorPalette.text
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                        scaleX = textScale
                        scaleY = textScale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                )

                val artistFade = (1f - (progress / 0.8f)).coerceIn(0f, 1f)
                BasicText(
                    text = metadata?.artist ?: "-",
                    style = typography.xs.secondary.copy(
                        color = colorPalette.textSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        // Collapse its height with the fade so the title glides to
                        // true center instead of snapping at the compact swap.
                        .height(artistLineHeight * artistFade)
                        .graphicsLayer {
                            alpha = artistFade
                            scaleX = textScale
                            scaleY = textScale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                )
            }

            val compactDock = AppearancePreferences.compactDock
            // The pill rests at progress 0 (expanded) and 1 (collapsed), so the
            // controls must be fully visible at both ends and only tuck away
            // mid-morph. `pulse` is 0 at both rest states, 1 at the midpoint.
            val controlsAlpha = 1f - 0.85f * pulse
            // Play/pause only once the pill is collapsed or the dock is compact;
            // next stays in the expanded dock where there is room for it.
            val showNext = !compactDock && progress < 0.5f
            Row(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = controlsAlpha
                        // Spin the controls as they tuck away
                        rotationZ = -15f * pulse
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeMediaItem != null) {
                    AnimatedContent(
                        targetState = shouldBePlaying to isBuffering,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = ""
                    ) { (isPlaying, buffering) ->
                        Box(
                            modifier = Modifier
                                .padding(all = if (compactDock) 6.dp else 4.dp)
                                .size(if (compactDock) 32.dp else 28.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        if (shouldBePlaying) binder?.player?.pause()
                                        else if (state.mediaItem != null) binder?.player?.play()
                                        else state.historyMediaItem?.let { binder?.player?.seamlessPlay(it) }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (buffering && isPlaying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(if (compactDock) 20.dp else 18.dp)
                                )
                            } else {
                                androidx.compose.foundation.Image(
                                    painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(colorPalette.accent),
                                    modifier = Modifier.size(if (compactDock) 22.dp else 20.dp)
                                )
                            }
                        }
                    }
                    if (showNext) {
                        Box(
                            modifier = Modifier
                                .padding(all = 4.dp)
                                .size(28.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { binder?.player?.seekToNext() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                painter = painterResource(R.drawable.play_skip_forward),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(colorPalette.accent),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

