package app.pulse.android.ui.components.themed

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pulse.android.R
import app.pulse.android.ui.components.NewMenu
import app.pulse.android.utils.medium
import app.pulse.android.utils.primary
import app.pulse.android.utils.semiBold
import app.pulse.core.ui.LocalAppearance

/**
 * Space above the title, roughly where the cover card used to sit, so the title
 * reads over the mosaic instead of hugging the top bar.
 */
private val HeroTopPadding = 360.dp

/**
 * Header body shared by the online and local playlist screens: the title, the
 * song count and the shuffle / play / add row, over the mosaic backdrop.
 */
@Composable
fun PlaylistHero(
    title: String,
    songCount: Int,
    mosaicUrls: List<String>,
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    onAdd: (() -> Unit)?,
    shuffleWithLabel: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    val enabled = songCount > 0

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = HeroTopPadding, bottom = 24.dp)
    ) {
        BasicText(
            text = title,
            style = typography.l.semiBold.copy(color = colorPalette.text),
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        BasicText(
            text = pluralStringResource(R.plurals.song_count_plural, songCount, songCount),
            style = typography.s.medium.copy(color = colorPalette.accent.copy(alpha = 0.7f)),
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.weight(1f))

            if (!shuffleWithLabel) HeaderCircleIconButton(
                icon = R.drawable.shuffle,
                containerColor = colorPalette.accent,
                contentColor = colorPalette.onAccent,
                enabled = enabled,
                onClick = onShuffle
            )

            HeroActionPill(
                icon = R.drawable.play,
                text = stringResource(R.string.play),
                enabled = enabled,
                onClick = onPlay
            )

            if (onAdd != null) HeaderCircleIconButton(
                icon = R.drawable.add,
                containerColor = colorPalette.accent,
                contentColor = colorPalette.onAccent,
                onClick = onAdd
            )

            if (shuffleWithLabel) HeroActionPill(
                icon = R.drawable.shuffle,
                text = stringResource(R.string.shuffle),
                enabled = enabled,
                onClick = onShuffle
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/** Accent pill with an icon and a label, used for the header's main actions. */
@Composable
private fun HeroActionPill(
    icon: Int,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(colorPalette.accent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette.onAccent),
            modifier = Modifier.size(18.dp)
        )

        BasicText(
            text = text,
            style = typography.s.semiBold.primary
        )
    }
}

/**
 * Sharp mosaic behind the song list, capped at 80% of the screen height and
 * faded into the background before the rows start, so the playlist cover reads
 * as the screen backdrop without swallowing the list.
 */
@Composable
fun PlaylistBackdrop(
    mosaicUrls: List<String>,
    lazyListState: LazyListState
) {
    if (mosaicUrls.isEmpty()) return

    val colorPalette = LocalAppearance.current.colorPalette

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f)
            .graphicsLayer {
                // Only while the header is the first visible item is its scroll
                // offset the true scroll position. Item offsets in layoutInfo are
                // relative to the viewport, so they restart each time the first
                // visible item changes, which snapped the mosaic back once per
                // row. Once the header has left, the mosaic has faded out anyway,
                // so park it off screen.
                val scrolled = if (lazyListState.firstVisibleItemIndex == 0)
                    lazyListState.firstVisibleItemScrollOffset.toFloat()
                else size.height

                translationY = -scrolled.coerceIn(0f, size.height)
            }
    ) {
        MosaicThumbnail(
            urls = mosaicUrls,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        0.75f to colorPalette.background0,
                        1f to colorPalette.background0
                    )
                )
        )
    }
}

/** Fades the screen bottom into the background behind the scrolling list. */
@Composable
fun PlaylistGradient() {
    val colorPalette = LocalAppearance.current.colorPalette

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.4f to Color.Transparent,
                    0.75f to colorPalette.background0,
                    1f to colorPalette.background0
                )
            )
    )
}

/**
 * Share button plus an overflow menu whose entries are supplied by the caller,
 * since an online playlist offers different actions than a local one.
 */
@Composable
fun PlaylistHeaderActions(
    shareUrl: String?,
    menuVisible: Boolean,
    onMenuVisibleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    menuContent: @Composable () -> Unit
) {
    val colorPalette = LocalAppearance.current.colorPalette
    val context = LocalContext.current

    HeaderPillRow(containerColor = colorPalette.accent, modifier = modifier) {
        IconButton(
            icon = R.drawable.share_social,
            onClick = {
                shareUrl?.let { url ->
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                }
            }
        )

        Box {
            IconButton(
                icon = R.drawable.ellipsis_horizontal,
                onClick = { onMenuVisibleChange(!menuVisible) }
            )

            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                NewMenu(
                    visible = menuVisible,
                    onDismiss = { onMenuVisibleChange(false) },
                    containerColor = colorPalette.accent
                ) {
                    menuContent()
                }
            }
        }
    }
}
