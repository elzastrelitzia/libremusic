package app.pulse.android.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pulse.android.LocalPlayerServiceBinder
import app.pulse.android.R
import app.pulse.android.preferences.PlayerPreferences
import app.pulse.android.utils.bold
import app.pulse.android.utils.color
import app.pulse.android.utils.shareToInstagramStory
import app.pulse.core.ui.LocalAppearance
import app.pulse.core.ui.overlay
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.material3
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

/**
 * Floating card picker for the lyric lines that go onto the story card.
 *
 * [initialIndex] is selected on entry and scrolled into view once; the list never
 * auto-scrolls after that and the selection is dropped when the screen leaves.
 */
@Composable
fun LyricsShareSelection(
    mediaId: String,
    lines: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    val (colorPalette, typography, _, thumbnailShape) = LocalAppearance.current
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val mediaItem = binder?.player?.currentMediaItem
    val artworkUri = mediaItem?.mediaMetadata?.artworkUri
    val lyricsFontSize =
        (PlayerPreferences.lyricsFontSize * CARD_FONT_SCALE).coerceIn(MIN_CARD_FONT, MAX_CARD_FONT).sp
    val hazeState = rememberHazeState()

    val initialSelection = if (
        lines.isNotEmpty() &&
        initialIndex in lines.indices &&
        !lines[initialIndex].isBlank()
    ) setOf(initialIndex) else emptySet()
    var selection by remember(mediaId) { mutableStateOf(initialSelection) }
    var limitHit by remember { mutableStateOf(false) }
    LaunchedEffect(limitHit) {
        if (limitHit) {
            delay(400)
            limitHit = false
        }
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex + 1)
    var initialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(initialIndex) {
        if (!initialScrollDone) {
            listState.scrollToItem(initialIndex + 1)
            initialScrollDone = true
        }
    }

    // Enter/exit animation lives here so the parent can drop the overlay state
    // immediately without cutting the exit slide.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    LaunchedEffect(shown) {
        if (!shown) {
            delay(EXIT_ANIMATION_MS)
            onDismiss()
        }
    }
    val dismiss = { shown = false }

    BackHandler(onBack = dismiss)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(ENTER_FADE_MS)),
            exit = fadeOut(tween(EXIT_SLIDE_MS)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colorPalette.overlay)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = dismiss,
                    )
            )
        }

        AnimatedVisibility(
            visible = shown,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxHeight(CARD_HEIGHT_FRACTION),
            enter = fadeIn(tween(ENTER_FADE_MS)) +
                slideInVertically(tween(ENTER_SLIDE_MS)) { it },
            exit = fadeOut(tween(EXIT_FADE_MS)) +
                slideOutVertically(tween(EXIT_SLIDE_MS)) { it },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(colorPalette.background0)
            ) {
                // Backdrop content captured for the share-button glass blur. The blur
                // button is a SIBLING of this box (below), never inside it, so the
                // capture/consumer graph stays acyclic and the render thread doesn't
                // crash (Cloudy-style cyclic RenderNode SIGSEGV).
                Box(
                    Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            )
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 96.dp),
                        ) {
                            item { Spacer(Modifier.height(HEADER_SPACE)) }
                            itemsIndexed(lines) { index, line ->
                                val selected = index in selection
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 6.dp)
                                        .clip(RoundedCornerShape(26.dp))
                                        .background(
                                            if (selected) {
                                                Brush.verticalGradient(
                                                    listOf(
                                                        colorPalette.accent.copy(alpha = 0.22f),
                                                        colorPalette.accent.copy(alpha = 0.10f),
                                                    )
                                                )
                                            } else {
                                                Brush.verticalGradient(
                                                    listOf(colorPalette.background1, colorPalette.background1)
                                                )
                                            }
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            if (line.isBlank()) return@clickable
                                            val updated = toggleSelection(lines, selection, index)
                                            if (updated == selection) {
                                                if (!selected) limitHit = true
                                            } else {
                                                selection = updated
                                            }
                                        }
                                        .padding(horizontal = 29.dp, vertical = 24.dp)
                                ) {
                                    if (line.isBlank()) {
                                        Image(
                                            painter = painterResource(R.drawable.musical_notes),
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(colorPalette.text.copy(alpha = 0.4f)),
                                            modifier = Modifier.size(lyricsFontSize.value.dp),
                                        )
                                    } else {
                                        BasicText(
                                            text = line,
                                            style = typography.l.bold.copy(fontSize = lyricsFontSize).color(
                                                if (selected) colorPalette.text
                                                else colorPalette.text.copy(alpha = 0.55f)
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(HEADER_FADE_HEIGHT)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        colorPalette.background0,
                                        colorPalette.background0.copy(alpha = 0.8f),
                                        Color.Transparent,
                                    )
                                )
                            )
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            )
                            .padding(start = 20.dp, top = 20.dp, end = 12.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (artworkUri != null) {
                            AsyncImage(
                                model = artworkUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(thumbnailShape),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(thumbnailShape)
                                    .background(colorPalette.background1)
                                )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                        ) {
                            BasicText(
                                text = pluralStringResource(
                                    R.plurals.share_lyrics_lines_selected,
                                    selection.size,
                                    selection.size,
                                ),
                                style = typography.m.bold.color(colorPalette.text),
                            )
                            Spacer(Modifier.height(2.dp))
                            BasicText(
                                text = stringResource(
                                    R.string.share_lyrics_characters,
                                    characterCount(lines, selection),
                                    MAX_SELECTION_CHARACTERS,
                                ),
                                style = typography.xs.color(
                                    if (limitHit) colorPalette.accent else colorPalette.textSecondary
                                ),
                            )
                        }

                        Image(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.share_lyrics_close),
                            colorFilter = ColorFilter.tint(colorPalette.text),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = dismiss)
                                .padding(12.dp)
                                .size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(170.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, colorPalette.background0)
                                )
                            )
                    )
                }

                // Share button overlay: sibling of hazeSource box so the glass blur
                // consumes captured backdrop content, not this node itself.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(top = 12.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val enabled = selection.isNotEmpty()
                    val glassStyle = GlassStyle.clear.material3().then {
                        backgroundColor(colorPalette.background1.copy(alpha = 0.75f))
                        tint(colorPalette.background1.copy(alpha = 0.30f))
                        specularIntensity(0f)
                        edgeSoftness(2.dp)
                        edgeShadow(Color.Transparent)
                    }
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .hazeGlass(
                                input = HazeInput.Backdrop(hazeState),
                                style = glassStyle,
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = enabled,
                            ) {
                                val item = binder?.player?.currentMediaItem ?: return@clickable
                                val selectedLines = selection.sorted().map { lines[it] }
                                dismiss()
                                shareToInstagramStory(context, item, selectedLines)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.share_social),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorPalette.text),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        text = stringResource(R.string.share_to_instagram_story),
                        style = typography.xs.color(
                            colorPalette.textSecondary.copy(alpha = if (enabled) 1f else 0.5f)
                        ),
                    )
                }
            }
        }
    }
}

private const val ENTER_FADE_MS = 180
private const val ENTER_SLIDE_MS = 300
private const val EXIT_FADE_MS = 180
private const val EXIT_SLIDE_MS = 240
private const val EXIT_ANIMATION_MS = 260L
private const val CARD_HEIGHT_FRACTION = 0.9f
private const val CARD_FONT_SCALE = 0.65f
private const val MIN_CARD_FONT = 14f
private const val MAX_CARD_FONT = 20f
private val HEADER_SPACE = 96.dp
private val HEADER_FADE_HEIGHT = 150.dp
