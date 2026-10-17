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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import app.pulse.android.utils.semiBold
import app.pulse.core.ui.LocalAppearance
import app.pulse.core.ui.utils.px

private val HeroShape = RoundedCornerShape(16.dp)

/**
 * Header body shared by the online and local playlist screens: a mosaic cover,
 * the title, the song count and the shuffle / play / add row.
 */
@Composable
fun PlaylistHero(
    title: String,
    songCount: Int,
    mosaicUrls: List<String>,
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    val enabled = songCount > 0

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 120.dp, bottom = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            MosaicThumbnail(
                urls = mosaicUrls,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        shape = HeroShape
                        shadowElevation = 8.dp.toPx()
                        clip = false
                    }
                    .clip(HeroShape)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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

            HeaderCircleIconButton(
                icon = R.drawable.shuffle,
                containerColor = colorPalette.accent,
                enabled = enabled,
                onClick = onShuffle
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colorPalette.accent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                        onClick = onPlay
                    )
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette.background0),
                    modifier = Modifier.size(18.dp)
                )

                BasicText(
                    text = stringResource(R.string.play),
                    style = typography.s.semiBold.copy(color = colorPalette.background0)
                )
            }

            if (onAdd != null) HeaderCircleIconButton(
                icon = R.drawable.add,
                containerColor = colorPalette.accent,
                onClick = onAdd
            )

            Spacer(modifier = Modifier.weight(1f))
        }
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
