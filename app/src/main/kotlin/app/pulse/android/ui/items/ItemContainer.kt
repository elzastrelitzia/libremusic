package app.pulse.android.ui.items

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pulse.android.utils.center
import app.pulse.android.utils.color
import app.pulse.android.utils.medium
import app.pulse.android.utils.secondary
import app.pulse.android.utils.semiBold
import app.pulse.core.ui.Dimensions
import app.pulse.core.ui.LocalAppearance
import app.pulse.core.ui.onOverlay
import app.pulse.core.ui.overlay
import app.pulse.core.ui.utils.roundedShape

@Composable
fun ItemContainer(
    alternative: Boolean,
    thumbnailSize: Dp,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable (centeredModifier: Modifier) -> Unit
) {
    val content = remember { movableContentOf(content) }

    if (alternative) Column(
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .padding(Dimensions.items.alternativePadding)
            .width(thumbnailSize)
    ) { content(Modifier.align(Alignment.CenterHorizontally)) }
    else Row(
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .padding(
                vertical = Dimensions.items.verticalPadding,
                horizontal = Dimensions.items.horizontalPadding
            )
            .fillMaxWidth()
    ) { content(Modifier.align(Alignment.CenterVertically)) }
}

@Composable
inline fun ItemInfoContainer(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) = Column(
    horizontalAlignment = horizontalAlignment,
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = modifier,
    content = content
)


@Composable
fun AlternativeCard(
    thumbnailContent: @Composable BoxScope.(modifier: Modifier) -> Unit,
    title: String?,
    subtitle: String?,
    thumbnailSize: Dp,
    modifier: Modifier = Modifier,
    badge: String? = null
) = ItemContainer(
    alternative = true,
    thumbnailSize = thumbnailSize,
    modifier = Modifier.clip(
        (LocalAppearance.current.thumbnailShapeCorners + Dimensions.items.alternativePadding).roundedShape
    ) then modifier
) { centeredModifier ->
    val (colorPalette, typography, thumbnailShapeCorners) = LocalAppearance.current

    Box(
        modifier = centeredModifier
            .clip(thumbnailShapeCorners.roundedShape)
            .background(color = colorPalette.background1)
            .sizeIn(
                minWidth = thumbnailSize,
                minHeight = thumbnailSize
            )
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        thumbnailContent(Modifier.fillMaxSize())

        badge?.let { ItemBadge(it) }
    }

    ItemInfoContainer(
        modifier = if (subtitle.isNullOrBlank()) centeredModifier else Modifier
    ) {
        BasicText(
            text = title.orEmpty(),
            style = typography.m.semiBold.let { if (subtitle.isNullOrBlank()) it.center else it },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (!subtitle.isNullOrBlank()) BasicText(
            text = subtitle,
            style = typography.xs.semiBold.secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun BoxScope.ItemBadge(text: String) {
    val (colorPalette, typography, thumbnailShapeCorners) = LocalAppearance.current

    BasicText(
        text = text,
        style = typography.xxs.medium.color(colorPalette.onOverlay),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(all = Dimensions.items.gap)
            .background(
                color = colorPalette.overlay,
                shape = (thumbnailShapeCorners - Dimensions.items.gap).coerceAtLeast(0.dp).roundedShape
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .align(Alignment.BottomEnd)
    )
}
