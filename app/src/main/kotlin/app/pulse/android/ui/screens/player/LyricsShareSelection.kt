package app.pulse.android.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Full-screen picker for the lyric lines that go onto the story card.
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
    val lyricsFontSize = PlayerPreferences.lyricsFontSize.sp

    var selection by remember(mediaId) { mutableStateOf(setOf(initialIndex)) }
    var limitHit by remember { mutableStateOf(false) }
    LaunchedEffect(limitHit) {
        if (limitHit) {
            delay(400)
            limitHit = false
        }
    }

    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize().background(colorPalette.background0)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
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
                        text = stringResource(R.string.share_lyrics_lines_selected, selection.size),
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
                        .clickable(onClick = onDismiss)
                        .padding(12.dp)
                        .size(20.dp)
                )
            }

            LazyColumn(
                state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex),
                modifier = Modifier.weight(1f),
            ) {
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
                item { Spacer(Modifier.height(96.dp)) }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val enabled = selection.isNotEmpty()
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .alpha(if (enabled) 1f else 0.5f)
                    .clip(CircleShape)
                    .background(colorPalette.background1)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                    ) {
                        val item = binder?.player?.currentMediaItem ?: return@clickable
                        val selectedLines = selection.sorted().map { lines[it] }
                        onDismiss()
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
