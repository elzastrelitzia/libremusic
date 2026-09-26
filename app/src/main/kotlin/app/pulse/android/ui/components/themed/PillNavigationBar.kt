package app.pulse.android.ui.components.themed

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.pulse.android.R
import app.pulse.android.preferences.AppearancePreferences
import app.pulse.core.ui.Dimensions
import app.pulse.core.ui.LocalAppearance
import androidx.compose.foundation.text.BasicText
import app.pulse.android.utils.semiBold
import androidx.compose.ui.unit.sp

@Composable
internal fun PillNavigationItem(
    tab: Tab,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    labelAlpha: Float = 0f
) {
    val (colorPalette, typography) = LocalAppearance.current
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colorPalette.background2 else colorPalette.background1,
        label = "backgroundColor"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) colorPalette.accent else colorPalette.textSecondary,
        label = "iconColor"
    )

    val compact = AppearancePreferences.compactDock
    val tabWidth = if (compact) 60.dp else 72.dp
    val tabIconSize = if (compact) 20.dp else 22.dp

    Column(
        modifier = Modifier
            .width(tabWidth)
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(tab.icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(iconColor),
            modifier = Modifier.size(tabIconSize)
        )

        if (labelAlpha > 0.05f) {
            Spacer(modifier = Modifier.height(2.dp * labelAlpha))
            BasicText(
                text = tab.title(),
                style = typography.xs.semiBold.copy(
                    color = iconColor,
                    fontSize = 9.sp
                ),           
                modifier = Modifier.graphicsLayer { alpha = labelAlpha },
                maxLines = 1
            )
        }
    }
}

@Composable
fun FloatingSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (colorPalette) = LocalAppearance.current
    val compact = AppearancePreferences.compactDock
    val iconSize = if (compact) 20.dp else 24.dp
    Box(
        modifier = modifier
            .background(colorPalette.background1, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.search),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette.accent),
            modifier = Modifier.size(iconSize)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MorphingNavigationBar(
    progress: Float,
    tabs: kotlinx.collections.immutable.ImmutableList<Tab>,
    tabIndex: Int,
    onTabChange: (Int) -> Unit,
    hiddenTabs: kotlinx.collections.immutable.ImmutableList<String>,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    val dockScrolled = LocalDockScrolled.current
    val lazyListState = rememberLazyListState()
    
    // Filter visible tabs (same logic as PillNavigationBar)
    val visibleTabsWithIndices = remember(tabs, hiddenTabs, tabIndex) {
        tabs.mapIndexed { index, tab -> index to tab }
            .filter { (index, tab) -> tab.key !in hiddenTabs || index == tabIndex }
    }

    Box(
        modifier = modifier
            .background(colorPalette.background1, CircleShape)
            .clip(CircleShape)
            .clickable(
                enabled = progress > 0.8f,
                onClick = { dockScrolled.value = false }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Widen the crossfade range for more fluidity (0.5 to 1.0)
        val crossfadeProgress = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
        val lazyRowAlpha = (1f - crossfadeProgress).coerceIn(0f, 1f)
        val singleIconAlpha = crossfadeProgress.coerceIn(0f, 1f)

        // 1. Full LazyRow (Rendered first, at the bottom of the stack)
        @Suppress("DEPRECATION")
        val overscrollConfig = LocalOverscrollConfiguration provides null
        CompositionLocalProvider(overscrollConfig) {
            Box(modifier = Modifier.graphicsLayer { alpha = lazyRowAlpha }) {
                LazyRow(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    itemsIndexed(visibleTabsWithIndices) { _, (originalIndex, tab) ->
                        val isSelected = originalIndex == tabIndex
                        val itemAlpha = if (isSelected) 1f else (1f - progress).coerceIn(0f, 1f)
                        Box(modifier = Modifier.graphicsLayer { alpha = itemAlpha }) {
                            PillNavigationItem(
                                tab = tab,
                                isSelected = isSelected,
                                onClick = { onTabChange(originalIndex) },
                                enabled = progress < 0.2f,
                                labelAlpha = (1f - progress * 3f).coerceIn(0f, 1f)
                            )
                        }
                    }
                }
            }
        }

        // 2. Single centered selected item (Rendered LAST so it's on top of the LazyRow)
        val selectedTab = tabs.getOrNull(tabIndex)
        if (selectedTab != null) {
            Box(modifier = Modifier.graphicsLayer { 
                alpha = singleIconAlpha 
                val scale = (0.8f + (singleIconAlpha * 0.2f)).coerceIn(0f, 1.2f)
                scaleX = scale
                scaleY = scale
            }) {
                PillNavigationItem(
                    tab = selectedTab,
                    isSelected = false,
                    onClick = { dockScrolled.value = false },
                    enabled = progress > 0.8f
                )
            }
        }
    }
}



@Composable
fun HomeCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (colorPalette) = LocalAppearance.current
    val compact = AppearancePreferences.compactDock
    val iconSize = if (compact) 20.dp else 24.dp
    Box(
        modifier = modifier
            .background(colorPalette.background1, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.home),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette.accent),
            modifier = Modifier.size(iconSize)
        )
    }
}
