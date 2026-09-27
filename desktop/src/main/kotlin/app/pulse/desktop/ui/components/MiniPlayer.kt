@file:Suppress("DEPRECATION")

package app.pulse.desktop.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pulse.core.data.models.LoopMode
import app.pulse.desktop.service.PlayerService
import app.pulse.desktop.ui.utils.NetworkImage
import app.pulse.desktop.ui.utils.adaptiveScale
import app.pulse.desktop.ui.constants.sizes.Sizes
import app.pulse.core.data.utils.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayer(
    player: PlayerService,
    isPlayerOpen: Boolean = false,
    onClick: () -> Unit,
    onOpenPlayer: () -> Unit = onClick,
    onClosePlayer: () -> Unit = {},
    onToggleQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by player.state.collectAsState()
    val song = state.currentSong ?: return

    val bg = Color(0xFF1e1e1e)
    val text = Color(0xFFf2f0eb)
    val dim = Color(0xFFa8a39a)
    val accent = Color(0xFFf2f0eb)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
    ) {
        val s = adaptiveScale(maxWidth)
        val density = LocalDensity.current

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.96f)
                .height((Sizes.miniPlayerH * s).dp)
                .shadow(if (isPlayerOpen) 0.dp else Sizes.miniPlayerShadow.dp, RoundedCornerShape(Sizes.miniPlayerCardRadius.dp))
                .clip(RoundedCornerShape(Sizes.miniPlayerCardRadius.dp))
                .background(bg, RoundedCornerShape(Sizes.miniPlayerCardRadius.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
               // .padding(horizontal = (24 * s).dp)
                .padding(
                    start = (Sizes.miniPlayerThumbPad * s).dp,
                    top = (4 * s).dp,
                    end = (Sizes.homeColumnPad * s).dp,
                    bottom = (4 * s).dp
                )
        ) {
            // left track info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.widthIn(max = (320 * s).dp)
            ) {
                Box(
                    modifier = Modifier
                        .size((Sizes.miniPlayerThumb * s).dp)
                        .clip(CircleShape)
                        .background(Color(0xFF141414))
                ) {
                    song.thumbnailUrl?.let { thumb ->
                        val thumbPx = with(density) { (Sizes.miniPlayerThumb * s).dp.toPx().toInt() }
                        NetworkImage(
                            url = thumb,
                            modifier = Modifier.size((80 * s).dp),
                            requestedSize = thumbPx
                        )
                    }
                }
                Spacer(Modifier.width((Sizes.miniPlayerTextStart * s).dp))
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = song.title,
                        color = text,
                        fontSize = (Sizes.miniPlayerIconTitle * s).sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val error = state.error
                    (error ?: song.artistsText)?.let { sub ->
                        Text(
                            text = sub,
                            color = if (error != null) Color(0xFFE05252) else dim,
                            fontSize = (Sizes.miniPlayerIconSub * s).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // center controls + seekbar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy((-8 * s).dp, Alignment.CenterVertically),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 0.dp)
                ) {
                    Icon(
                        painter = painterResource("/icons/shuffle.svg"),
                        contentDescription = "Shuffle",
                        tint = dim,
                        modifier = Modifier.size((Sizes.miniPlayerIconSm * s).dp)
                    )
                    Spacer(Modifier.width((Sizes.miniPlayerSpacerLg * s).dp))

                    IconButton16(
                        painter = painterResource("/icons/play_skip_back.svg"),
                        desc = "Previous",
                        tint = text,
                        size = (Sizes.miniPlayerIconMd * s).dp,
                        onClick = { player.playPrevious() }
                    )
                    Spacer(Modifier.width((Sizes.miniPlayerSpacerSm * s).dp))

                    Box(
                        modifier = Modifier
                            .size((Sizes.miniPlayerIconLg * s).dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!state.isLoading) {
                                    if (state.isPlaying) player.pause() else player.resume()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size((32 * s).dp),
                                color = accent,
                                strokeWidth = (3 * s).dp
                            )
                        } else {
                            Icon(
                                painter = painterResource(
                                    if (state.isPlaying) "/icons/pause.svg" else "/icons/play.svg"
                                ),
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Color(0xFFFFFFFF),
                                modifier = Modifier.size((32 * s).dp)
                            )
                        }
                    }
                    Spacer(Modifier.width((Sizes.miniPlayerSpacerSm * s).dp))

                    IconButton16(
                        painter = painterResource("/icons/play_skip_forward.svg"),
                        desc = "Next",
                        tint = text,
                        size = (Sizes.miniPlayerIconMd * s).dp,
                        onClick = { player.playNext() }
                    )
                    Spacer(Modifier.width((Sizes.miniPlayerSpacerLg * s).dp))

                    Icon(
                        painter = painterResource(
                            when (state.loopMode) {
                                LoopMode.ONE -> "/icons/repeat_on.svg"
                                else -> "/icons/repeat.svg"
                            }
                        ),
                        contentDescription = "Repeat",
                        tint = when (state.loopMode) {
                            LoopMode.NONE -> dim.copy(alpha = 0.4f)
                            LoopMode.ONE, LoopMode.ALL -> accent
                        },
                        modifier = Modifier
                            .size((20 * s).dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { player.cycleLoopMode() }
                            )
                    )
                }

                // seekbar row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.width((Sizes.miniPlayerSeekW * s).dp)
                ) {
                    Text(
                        text = formatDuration(state.currentPositionMs),
                        color = dim,
                        fontSize = (Sizes.miniPlayerSeekTimeFont * s).sp,
                        modifier = Modifier.width((Sizes.miniPlayerSeekTimeW * s).dp)
                    )
                    val seekInteractionSource = remember { MutableInteractionSource() }
                    val isSeekHovered by seekInteractionSource.collectIsHoveredAsState()
                    val isSeekPressed by seekInteractionSource.collectIsPressedAsState()
                    val isSeekDragged by seekInteractionSource.collectIsDraggedAsState()
                    val isSeekActive = isSeekHovered || isSeekPressed || isSeekDragged
                    val seekHeight by animateDpAsState(if (isSeekActive) (12 * s).dp else (6 * s).dp)
                    val seekWidthScale by animateFloatAsState(if (isSeekActive) 1.02f else 1f)

                    var isDragging by remember { mutableStateOf(false) }
                    var dragFraction by remember { mutableFloatStateOf(0f) }

                    val displayFraction = if (isDragging) dragFraction
                    else if (state.durationMs > 0)
                        (state.currentPositionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                    else 0f

                    Slider(
                        value = displayFraction,
                        onValueChange = { fraction ->
                            isDragging = true
                            dragFraction = fraction
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            player.seek((dragFraction * state.durationMs).toLong())
                        },
                        interactionSource = seekInteractionSource,
                        track = { sliderState ->
                            SliderTrack(
                                fraction = sliderState.value,
                                height = seekHeight,
                                activeColor = accent,
                                widthScale = seekWidthScale
                            )
                        },
                        thumb = {},
                        modifier = Modifier.weight(1f).height((32 * s).dp)
                    )
                    Text(
                        text = formatDuration(state.durationMs),
                        color = dim,
                        fontSize = (11 * s).sp,
                        modifier = Modifier.width((Sizes.miniPlayerSeekTimeW * s).dp)
                    )
                }
            }

            // right volume
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.widthIn(max = (320 * s).dp)
            ) {
                Spacer(Modifier.width((Sizes.miniPlayerSpacerSide * s).dp))
                Icon(
                    painter = painterResource("/icons/lyrics.svg"),
                    contentDescription = "Now Playing",
                    tint = dim,
                    modifier = Modifier.size((26 * s).dp)
                )
                Spacer(Modifier.width((Sizes.miniPlayerSpacerSide * s).dp))
                Icon(
                    painter = painterResource("/icons/list.svg"),
                    contentDescription = "Queue",
                    tint = accent,
                    modifier = Modifier
                        .size((Sizes.miniPlayerIconSide * s).dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleQueue
                        )
                )
                Spacer(Modifier.width((Sizes.miniPlayerSpacerSide * s).dp))
                Icon(
                    painter = painterResource(
                        if (state.volume <= 0f) "/icons/volume_muted.svg" else "/icons/volume_up.svg"
                    ),
                    contentDescription = "Volume",
                    tint = dim,
                    modifier = Modifier.size((Sizes.miniPlayerIconSide * s).dp)
                )
                Spacer(Modifier.width((Sizes.miniPlayerSpacerVol * s).dp))

                val volInteractionSource = remember { MutableInteractionSource() }
                val isVolHovered by volInteractionSource.collectIsHoveredAsState()
                val isVolPressed by volInteractionSource.collectIsPressedAsState()
                val isVolDragged by volInteractionSource.collectIsDraggedAsState()
                val isVolActive = isVolHovered || isVolPressed || isVolDragged
                val volHeight by animateDpAsState(if (isVolActive) (12 * s).dp else (6 * s).dp)

                Slider(
                    value = state.volume,
                    onValueChange = { player.setVolume(it) },
                    interactionSource = volInteractionSource,
                    track = { sliderState ->
                        SliderTrack(
                            fraction = sliderState.value,
                            height = volHeight,
                            activeColor = dim
                        )
                    },
                    thumb = {},
                    modifier = Modifier.width((Sizes.miniPlayerVolW * s).dp).height((32 * s).dp)
                )
                Spacer(Modifier.width((14 * s).dp))
                Icon(
                    painter = painterResource(
                        if (isPlayerOpen) "/icons/minimize.svg" else "/icons/expand.svg"
                    ),
                    contentDescription = if (isPlayerOpen) "Minimize" else "Fullscreen",
                    tint = text,
                    modifier = Modifier
                        .size((Sizes.miniPlayerIconSide * s).dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = if (isPlayerOpen) onClosePlayer else onOpenPlayer
                        )
                )
            }
        }
    }
}

@Composable
private fun SliderTrack(
    fraction: Float,
    height: Dp,
    activeColor: Color,
    widthScale: Float = 1f
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthScale)
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(CircleShape)
                .background(Color(0xFF2a2a2a))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(height)
                .clip(CircleShape)
                .background(activeColor)
                .align(Alignment.CenterStart)
        )
    }
}

@Composable
private fun IconButton16(
    painter: Painter,
    desc: String,
    tint: Color,
    size: Dp = 20.dp,
    onClick: () -> Unit
) {
    Icon(
        painter = painter,
        contentDescription = desc,
        tint = tint,
        modifier = Modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    )
}
