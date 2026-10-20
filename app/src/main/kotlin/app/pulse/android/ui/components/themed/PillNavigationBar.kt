package app.pulse.android.ui.components.themed

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pulse.android.R
import app.pulse.android.preferences.AppearancePreferences
import app.pulse.core.ui.Dimensions
import app.pulse.core.ui.LocalAppearance
import androidx.compose.foundation.text.BasicText
import app.pulse.android.utils.semiBold
import androidx.compose.ui.unit.sp

// The dock is only interactive in its expanded state. Both the gesture guard and the pill
// layer gate on the same morph progress, so the threshold lives here once.
private const val EXPANDED_PROGRESS = 0.8f

@Composable
internal fun PillNavigationItem(
    tab: Tab,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    labelAlpha: Float = 0f,
    tabWidth: Dp = if (AppearancePreferences.compactDock) 60.dp else 72.dp,
    // Separate from isSelected
    iconSelected: Boolean = isSelected
) {
    val (colorPalette, typography) = LocalAppearance.current
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) colorPalette.accent else colorPalette.textSecondary,
        label = "iconColor"
    )

    val compact = AppearancePreferences.compactDock
    val tabIconSize = if (compact) 20.dp else 22.dp

    Column(
        modifier = Modifier
            .width(tabWidth)
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ) else Modifier
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(
                if (iconSelected) tab.selectedIcon ?: tab.icon else tab.icon
            ),
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
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
    onAction: (String) -> Unit,
    hiddenTabs: kotlinx.collections.immutable.ImmutableList<String>,
    // The bar's width when fully expanded. Tab widths come from this, not from the live
    // width, so the row does not reflow while the bar contracts around it.
    expandedWidth: Dp = Dp.Unspecified,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    val dockScrolled = LocalDockScrolled.current
    val lazyListState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    // The pill is the dock's only selection highlight. It sits on the selected tab at rest
    // and follows the finger during a drag.
    var dragPillX by remember { mutableStateOf<Float?>(null) }
    var dragTabIndex by remember { mutableStateOf<Int?>(null) }
    // Release speed and landing tab of the last drag, consumed by the settle animation.
    var releaseVelocity by remember { mutableStateOf(0f) }
    var releaseTargetIndex by remember { mutableStateOf<Int?>(null) }
    // The pill's centre in bar-local pixels.
    val pillCentre = remember { Animatable(0f) }

    // Each tab cell's centre, measured from the real composables. Keyed by the tab's
    // original index. Measured rather than derived from LazyList layout info so the pill
    // always matches where the tab actually is.
    val tabCentres = remember { mutableStateMapOf<Int, Float>() }

    // Filter visible tabs (same logic as PillNavigationBar)
    val visibleTabsWithIndices = remember(tabs, hiddenTabs, tabIndex) {
        tabs.mapIndexed { index, tab -> index to tab }
            .filter { (index, tab) -> tab.key !in hiddenTabs || index == tabIndex }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(colorPalette.background1, CircleShape)
            .clip(CircleShape)
            .clickable(
                enabled = progress > EXPANDED_PROGRESS,
                onClick = { dockScrolled.value = false }
            )
            .pointerInput(visibleTabsWithIndices, progress) {
                if (progress > EXPANDED_PROGRESS) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // A new touch supersedes any drop still settling from the last one.
                    // Without this, a tap arriving mid-settle leaves releaseTargetIndex
                    // pointing at the previous landing tab and strands the pill there.
                    releaseVelocity = 0f
                    releaseTargetIndex = null

                    // Item offsets and pointer positions are both relative to this Box, so
                    // they compare directly. Do not add padding or viewport arithmetic here.
                    fun tabIndexAt(x: Float): Int? = lazyListState.layoutInfo.visibleItemsInfo
                        .firstOrNull { x >= it.offset && x < it.offset + it.size }
                        ?.let { info -> visibleTabsWithIndices.getOrNull(info.index)?.first }

                    // The tab the pill is currently over, used to fire haptics on change.
                    fun tabUnderPill(centre: Float): Int? =
                        tabIndexAt(centre) ?: tabIndexAt(down.position.x)

                    var dragging = false
                    // Release speed, so a flick springs past the tab and wobbles back.
                    val velocityTracker = VelocityTracker()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                        // A drag only becomes a selection drag once it moves sideways
                        // past a small threshold, so a tap still lands as a click.
                        if (!dragging) {
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (dx * dx + dy * dy < 32f * 32f) {
                                if (change.changedToUpIgnoreConsumed()) break
                                continue
                            }
                            if (kotlin.math.abs(dx) < kotlin.math.abs(dy)) break
                            dragging = true
                            dragTabIndex = tabIndexAt(down.position.x)
                        }

                        change.consume()
                        if (change.changedToUpIgnoreConsumed()) break

                        // Follow the finger exactly: the effect below snaps the pill to
                        // dragPillX, so there is no spring lag.
                        dragPillX = change.position.x
                        val newIndex = tabUnderPill(change.position.x)
                        if (newIndex != dragTabIndex) {
                            dragTabIndex = newIndex
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }

                    if (dragging) {
                        val landedIndex = dragTabIndex
                        if (landedIndex != null) {
                            // The velocity at release drives the wobble, so a fast flick
                            // overshoots the tab and springs back, a slow drag barely moves
                            // past it.
                            releaseVelocity = velocityTracker.calculateVelocity().x
                            // Keep the pill where the finger left it. The effect animates
                            // from here to the landing cell once the selection commits.
                            dragPillX = null
                            // Guard against animating to the stale resting centre, which
                            // still refers to the tab the drag started from.
                            releaseTargetIndex = landedIndex
                            val action = tabs.getOrNull(landedIndex)?.actionId
                            if (action != null) onAction(action) else onTabChange(landedIndex)
                        }
                    }
                    dragTabIndex = null
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Fit the visible tabs into the width the dock gave this bar, instead of a fixed
        // per-tab width. The width is the expanded one, so the row is laid out once and stays
        // put while the bar contracts; using the live width made the tabs slide and the pill
        // trail them by a frame, which read as a stagger.
        val compactDock = AppearancePreferences.compactDock
        val maxTabWidth = if (compactDock) 60.dp else 72.dp
        val layoutWidth = expandedWidth.takeIf { it != Dp.Unspecified } ?: maxWidth
        val tabWidth = visibleTabsWithIndices
            .takeIf { it.isNotEmpty() }
            ?.let { ((layoutWidth - 16.dp) / it.size).coerceIn(56.dp, maxTabWidth) }
            ?: maxTabWidth
        // Widen the crossfade range for more fluidity (0.5 to 1.0)
        val crossfadeProgress = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
        val lazyRowAlpha = (1f - crossfadeProgress).coerceIn(0f, 1f)
        val singleIconAlpha = crossfadeProgress.coerceIn(0f, 1f)

        // The dock's only selection highlight: one pill that rests on the selected tab and
        // follows the finger during a drag.
        val density = LocalDensity.current
        val pillWidthPx = with(density) { tabWidth.toPx() }
        val maxWidthPx = with(density) { maxWidth.toPx() }

        // Centre of the selected tab's cell, measured from the tab composable itself, so the
        // pill cannot drift from where the tab actually is.
        val restingCentrePx = tabCentres[tabIndex]

        // A drag drives pillCentre directly, otherwise it springs to the selected tab.
        // A flick release carries its velocity into a fast under-damped spring, so it
        // overshoots and wobbles back. A tap has no velocity and uses a slower spring, so the
        // travel across the dock is actually visible instead of a jump.
        LaunchedEffect(dragPillX, dragTabIndex, tabIndex, restingCentrePx, releaseTargetIndex) {
            val dragX = dragPillX
            if (dragX != null) {
                pillCentre.snapTo(dragX)
                return@LaunchedEffect
            }
            val target = restingCentrePx ?: return@LaunchedEffect
            // A drop is in flight, its landing tab has not been selected yet, so the resting
            // centre still points at the tab the drag started from. Wait for the selection to
            // commit rather than animating backwards. A new touch clears this target, so it
            // can never block a later tap.
            val pending = releaseTargetIndex
            if (pending != null && tabIndex != pending) return@LaunchedEffect

            // Non-zero means the change came from a drag release, so keep the snappy wobble.
            // Clamped so a very fast flick cannot fling the pill past neighbouring tabs.
            val flickVelocity = releaseVelocity.coerceIn(-4000f, 4000f)
            releaseVelocity = 0f

            try {
                pillCentre.animateTo(
                    target,
                    initialVelocity = flickVelocity,
                    animationSpec = if (flickVelocity != 0f) {
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    } else {
                        spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    }
                )
            } finally {
                // Consume the target even if this animation is cancelled by the next tab
                // change, so a stranded target can never stall a later animation.
                releaseTargetIndex = null
            }
        }

        if (progress <= EXPANDED_PROGRESS) {
            // Mid-morph the bar can be narrower than one tab, since tabWidth has a floor.
            // An inverted range would throw, so fall back to the bar's own centre.
            val halfPill = pillWidthPx / 2f
            val clampedCentre = if (maxWidthPx > pillWidthPx) {
                pillCentre.value.coerceIn(halfPill, maxWidthPx - halfPill)
            } else {
                maxWidthPx / 2f
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((clampedCentre - pillWidthPx / 2f).toInt(), 0) }
                    .width(tabWidth)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
                    .clip(CircleShape)
                    .background(colorPalette.background2)
            )
        }

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
                        Box(
                            modifier = Modifier
                                .graphicsLayer { alpha = itemAlpha }
                                .onGloballyPositioned { coordinates ->
                                    val bounds = coordinates.boundsInParent()
                                    tabCentres[originalIndex] = bounds.center.x
                                }
                        ) {
                            PillNavigationItem(
                                tab = tab,
                                isSelected = isSelected,
                                onClick = {
                                    val action = tab.actionId
                                    if (action != null) onAction(action) else onTabChange(originalIndex)
                                },
                                enabled = progress < 0.2f,
                                labelAlpha = (1f - progress * 3f).coerceIn(0f, 1f),
                                tabWidth = tabWidth
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
                    enabled = progress > 0.8f,
                    tabWidth = tabWidth,
                    iconSelected = true
                )
            }
        }
    }
}



@Composable
fun HomeCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Int = R.drawable.home
) {
    val (colorPalette) = LocalAppearance.current
    val compact = AppearancePreferences.compactDock
    val iconSize = if (compact) 20.dp else 24.dp
    Box(
        modifier = modifier
            .background(colorPalette.background1, CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette.accent),
            modifier = Modifier.size(iconSize)
        )
    }
}
