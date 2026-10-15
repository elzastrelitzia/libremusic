package app.pulse.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import app.pulse.android.models.ui.UiMedia
import app.pulse.android.preferences.PlayerPreferences
import app.pulse.android.service.PlayerService
import app.pulse.android.utils.formatAsDuration
import app.pulse.android.utils.semiBold
import app.pulse.core.ui.LocalAppearance
import app.pulse.core.ui.utils.roundedShape
import kotlin.math.PI
import kotlin.math.sin

// TODO: de-couple from binder

@Composable
fun SeekBar(
    binder: PlayerService.Binder,
    position: Long,
    media: UiMedia,
    modifier: Modifier = Modifier,
    color: Color = LocalAppearance.current.colorPalette.text,
    backgroundColor: Color = LocalAppearance.current.colorPalette.background2,
    shape: Shape = 8.dp.roundedShape,
    isActive: Boolean = binder.player.isPlaying,
    alwaysShowDuration: Boolean = false,
    scrubberRadius: Dp = 8.dp,
    enabled: Boolean = true,
    range: ClosedRange<Long> = 0L..media.duration
) {
    var scrubbingPosition by remember(media) { mutableStateOf<Long?>(null) }
    val animatedPosition by animateFloatAsState(
        targetValue = scrubbingPosition?.toFloat() ?: position.toFloat(),
        label = ""
    )

    var isDragging by remember { mutableStateOf(false) }

    val onSeekStart: (Long) -> Unit = { scrubbingPosition = it }
    val onSeek: (Long) -> Unit = { delta ->
        scrubbingPosition = if (media.duration == C.TIME_UNSET) null
        else scrubbingPosition?.let { (it + delta).coerceIn(range) }
    }
    val onSeekEnd = {
        scrubbingPosition?.let(binder.player::seekTo)
        scrubbingPosition = null
    }

    val innerModifier = modifier
        .pointerInput(range, enabled) {
            if (!enabled) return@pointerInput
            if (range.endInclusive < range.start) return@pointerInput

            detectDrags(
                setIsDragging = { isDragging = it },
                range = range,
                onSeekStart = onSeekStart,
                onSeek = onSeek,
                onSeekEnd = onSeekEnd
            )
        }
        .pointerInput(range, enabled) {
            if (!enabled) return@pointerInput
            detectTaps(
                range = range,
                onSeekStart = onSeekStart,
                onSeekEnd = onSeekEnd
            )
        }

    ClassicSeekBarBody(
        position = scrubbingPosition ?: animatedPosition.toLong(),
        duration = media.duration,
        poiTimestamp = binder.poiTimestamp,
        isDragging = isDragging,
        color = color,
        backgroundColor = backgroundColor,
        showDuration = alwaysShowDuration || scrubbingPosition != null,
        modifier = innerModifier,
        scrubberRadius = scrubberRadius,
        shape = shape
    )
}

@Composable
private fun ClassicSeekBarBody(
    position: Long,
    duration: Long,
    poiTimestamp: Long?,
    isDragging: Boolean,
    color: Color,
    backgroundColor: Color,
    scrubberRadius: Dp,
    shape: Shape,
    showDuration: Boolean,
    modifier: Modifier = Modifier,
    range: ClosedRange<Long> = 0L..duration,
    barHeight: Dp = 7.dp,
    scrubberColor: Color = color,
    drawSteps: Boolean = false
) = Column {
    val transition = updateTransition(
        targetState = isDragging,
        label = null
    )

    val currentBarHeight by transition.animateDp(label = "") { if (it) scrubberRadius else barHeight }

            Box(
        modifier = modifier
            .drawWithContent {
                drawContent()

                val scrubberPosition =
                    if (range.endInclusive < range.start) 0f
                    else (position.toFloat() - range.start) / (range.endInclusive - range.start) * size.width

                if (isDragging) {
                    val thumbWidth = 10.dp.toPx()
                    val thumbHeight = 20.dp.toPx()
                    drawRoundRect(
                        color = scrubberColor,
                        topLeft = Offset(
                            x = scrubberPosition - thumbWidth / 2f,
                            y = (size.height - thumbHeight) / 2f
                        ),
                        size = Size(thumbWidth, thumbHeight),
                        cornerRadius = CornerRadius(thumbHeight / 2f)
                    )
                }

                if (poiTimestamp != null && position < poiTimestamp) drawPoi(
                    range = range,
                    position = poiTimestamp,
                    color = color
                )

                if (drawSteps) for (i in position + 1..range.endInclusive) {
                    val stepPosition =
                        (i.toFloat() - range.start) / (range.endInclusive - range.start) * size.width

                    drawCircle(
                        color = scrubberColor,
                        radius = scrubberRadius.toPx() / 2,
                        center = center.copy(x = stepPosition)
                    )
                }
            }
            .height(scrubberRadius)
    ) {
        Spacer(
            modifier = Modifier
                .height(currentBarHeight)
                .fillMaxWidth()
                .background(color = backgroundColor, shape = shape)
                .align(Alignment.Center)
        )

        Spacer(
            modifier = Modifier
                .height(currentBarHeight)
                .fillMaxWidth((position.toFloat() - range.start) / (range.endInclusive - range.start).toFloat())
                .background(color = color, shape = shape)
                .align(Alignment.CenterStart)
        )
    }

    Duration(
        position = position,
        duration = duration,
        show = showDuration,
        color = color
    )
}

private suspend fun PointerInputScope.detectDrags(
    setIsDragging: (Boolean) -> Unit,
    range: ClosedRange<Long>,
    onSeekStart: (updated: Long) -> Unit,
    onSeek: (delta: Long) -> Unit,
    onSeekEnd: () -> Unit
) {
    var acc = 0f

    detectHorizontalDragGestures(
        onDragStart = { offset ->
            setIsDragging(true)
            onSeekStart((offset.x / size.width * (range.endInclusive - range.start).toFloat() + range.start).toLong())
        },
        onHorizontalDrag = { _, delta ->
            acc += delta / size.width * (range.endInclusive - range.start).toFloat()

            if (acc !in -1f..1f) {
                onSeek(acc.toLong())
                acc -= acc.toLong()
            }
        },
        onDragEnd = {
            setIsDragging(false)
            acc = 0f
            onSeekEnd()
        },
        onDragCancel = {
            setIsDragging(false)
            acc = 0f

            onSeekEnd()
        }
    )
}

private suspend fun PointerInputScope.detectTaps(
    range: ClosedRange<Long>,
    onSeekStart: (updated: Long) -> Unit,
    onSeekEnd: () -> Unit
) {
    if (range.endInclusive < range.start) return

    detectTapGestures(
        onTap = { offset ->
            onSeekStart(
                (offset.x / size.width * (range.endInclusive - range.start).toFloat() + range.start).toLong()
            )
            onSeekEnd()
        }
    )
}

private fun ContentDrawScope.drawScrubber(
    range: ClosedRange<Long>,
    position: Long,
    color: Color,
    height: Dp
) {
    val scrubberPosition = if (range.endInclusive < range.start) 0f
    else (position - range.start) / (range.endInclusive - range.start).toFloat() * size.width

    val widthPx = 5.dp.toPx()
    val heightPx = height.toPx()

    drawRoundRect(
        color = color,
        topLeft = Offset(
            x = scrubberPosition - widthPx / 2,
            y = (size.height - heightPx) / 2f
        ),
        size = Size(
            width = widthPx,
            height = heightPx
        ),
        cornerRadius = CornerRadius(widthPx / 2)
    )
}

private fun ContentDrawScope.drawPoi(
    range: ClosedRange<Long>,
    position: Long,
    color: Color,
    width: Dp = 4.dp
) {
    val poiPosition = if (range.endInclusive < range.start) 0f
    else (position - range.start) / (range.endInclusive - range.start).toFloat() * size.width

    val widthPx = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(x = poiPosition - widthPx / 2, y = 0f),
        size = Size(width = widthPx, height = size.height),
        cornerRadius = CornerRadius((width / 2).toPx())
    )
}

@Composable
private fun Duration(
    position: Long,
    duration: Long,
    show: Boolean,
    color: Color
) = AnimatedVisibility(
    visible = show,
    enter = fadeIn() + expandVertically { -it },
    exit = fadeOut() + shrinkVertically { -it }
) {
    val (_, typography) = LocalAppearance.current
    val fadedText = color.copy(alpha = 0.8f)

    Column {
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            BasicText(
                text = formatAsDuration(position),
                style = typography.xxs.semiBold.copy(fontSize = 12.sp, color = fadedText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            BasicText(
                text = if (duration != C.TIME_UNSET) "-${formatAsDuration((duration - position).coerceAtLeast(0L))}"
                else "--:--",
                style = typography.xxs.semiBold.copy(fontSize = 12.sp, color = fadedText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
