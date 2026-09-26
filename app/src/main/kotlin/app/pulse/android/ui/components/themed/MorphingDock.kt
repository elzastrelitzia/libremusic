package app.pulse.android.ui.components.themed

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.pulse.android.preferences.AppearancePreferences
import app.pulse.android.ui.components.MorphingMiniPlayer
import app.pulse.core.ui.Dimensions
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.pulse.core.ui.LocalAppearance

@Composable
fun MorphingDock(
    progress: Float,
    navigationState: NavigationState?,
    onPlayerClick: () -> Unit,
    onSearchClick: () -> Unit,
    onHomeClick: () -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    val compact = AppearancePreferences.compactDock
    val dockPad = if (compact) 16.dp else 24.dp
    val dockSpacing = if (compact) 6.dp else 8.dp
    val dockHeightExtra = if (compact) 64.dp else 80.dp
    val dockCompactWidth = if (compact) 200.dp else 240.dp
    val dockOverscrollDip = if (compact) 12.dp else 16.dp

    val isSubPage = navigationState == null
    val density = LocalDensity.current
    val (colorPalette) = LocalAppearance.current

    val animatable = remember { Animatable(progress) }

    // Capture the last valid progress from the home page to ensure a stable
    // handoff. Prevents flashes if `progress` resets to 0.0 during navigation.
    // We ignore resets to 0.0 (likely navigation artifacts) if we were already scrolled.
    // this is the work states, after many tries i fix it to remember last valid progress
    val lastValidHomeProgress = remember { mutableFloatStateOf(0f) }
    SideEffect {
        if (!isSubPage && progress > 0.01f) {
            lastValidHomeProgress.floatValue = progress
        }
    }

    // Keep the last navigation state so the bar can fade out instead of vanishing
    val lastNavState = remember { mutableStateOf(navigationState) }
    LaunchedEffect(navigationState) {
        if (navigationState != null) lastNavState.value = navigationState
    }

    // ── Single effect no frame gap between snap and spring ─────────────────
    //
    // The original had two separate LaunchedEffect blocks keyed on
    // (progress, isSubPage) and (isSubPage) respectively. When isSubPage flips,
    // Compose restarts BOTH effects in the same frame. The snap in the first
    // effect and the spring in the second effect ran in separate coroutine
    // dispatches, leaving a 1-frame window where animatable.value was stale.
    //
    // One effect keyed on (isSubPage, progress) handles both cases in a single
    // coroutine body the snapTo and animateTo are sequential with no gap.
    LaunchedEffect(isSubPage, progress) {
        if (!isSubPage) {
            // Home page: track scroll exactly.
            // stop() cancels any in-flight spring before snapping.
            animatable.stop()
            animatable.snapTo(progress)
        } else {
            // Sub-page entry: ensure we pick up from where we left off.
            // We snap to at least 1.0f (compact) or the last valid scrolled progress
            // to prevent the expansion flash (p=0).
            val startPoint = lastValidHomeProgress.floatValue.coerceAtLeast(1f)
            if (animatable.value < startPoint) {
                animatable.snapTo(startPoint)
            }

            if (animatable.value < 2f) {
                animatable.animateTo(
                    targetValue = 2f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
        }
    }


    // Single source of truth for the entire timeline.
    val p = if (!isSubPage) progress
            else animatable.value.coerceAtLeast(lastValidHomeProgress.floatValue)

    // Phase 1 (Home Morph: 0.0 → 1.0)
    // homeP is allowed to exceed 1.0 on home for overscroll wobble.
    // On sub-page it is clamped so overscroll is always 0.
    val homeP = if (!isSubPage) p else p.coerceAtMost(1f)

    val navMorphProgress    = (homeP / 0.90f).coerceIn(0f, 1f)
    val playerMorphProgress = ((homeP - 0.2f) / 0.7f).coerceIn(0f, 1f)
    val playerSlideProgress = ((homeP - 0.5f) / 0.4f).coerceIn(0f, 1f)

    // Phase 2 (Sub-page Entry: 1.0 → 2.0)
    val navHideFactor   = ((p - 1f) / 0.5f).coerceIn(0f, 1f)
    val radioShowFactor = ((p - 1.5f) / 0.5f).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        colorPalette.background0.copy(alpha = 0.8f),
                        colorPalette.background0
                    )
                )
            )
            .safeDrawingPadding()
            .padding(horizontal = dockPad, vertical = dockPad)
            .fillMaxWidth()
            .height(Dimensions.items.collapsedPlayerHeight * 2 + dockSpacing + dockHeightExtra)
    ) {
        val fullWidth = maxWidth
        val baseSize  = if (compact) Dimensions.items.collapsedPlayerHeight else 64.dp

        // Structural morph, shrink circle to 80% of baseSize at p=1.0
        val morphFactor       = homeP.coerceIn(0f, 1f)
        val targetSize        = baseSize * (1f - 0.2f * morphFactor)
        val overscroll        = (homeP - 1f).coerceAtLeast(0f)
        val currentCircleSize = (targetSize - (baseSize * 0.4f * overscroll)).coerceAtLeast(baseSize * 0.6f)
        val commonDip         = with(density) { dockOverscrollDip.toPx() * overscroll }

        // Search btn
        Box(
            modifier = Modifier
                .size(currentCircleSize)
                .align(Alignment.BottomEnd)
                .graphicsLayer {
                    alpha        = 1f - navHideFactor
                    scaleX       = 1f - navHideFactor
                    scaleY       = 1f - navHideFactor
                    translationY = commonDip
                }
        ) {
            FloatingSearchButton(onClick = onSearchClick, modifier = Modifier.fillMaxSize())
        }

        // Navigation bar
        val currentNavState = lastNavState.value
        if (!isLandscape && currentNavState != null && (1f - navHideFactor) > 0f) {
            val expandedNavWidth = fullWidth - baseSize - dockSpacing
            val currentNavWidth  = (expandedNavWidth + (currentCircleSize - expandedNavWidth) * navMorphProgress)
                .coerceAtLeast(currentCircleSize)

            Box(
                modifier = Modifier
                    .height(currentCircleSize)
                    .width(currentNavWidth)
                    .align(Alignment.BottomStart)
                    .graphicsLayer {
                        alpha        = 1f - navHideFactor
                        scaleX       = 1f - navHideFactor
                        scaleY       = 1f - navHideFactor
                        translationY = commonDip
                    }
            ) {
                MorphingNavigationBar(
                    progress        = navMorphProgress,
                    tabs            = currentNavState.tabs,
                    tabIndex        = currentNavState.tabIndex,
                    onTabChange     = currentNavState.onTabChange,
                    hiddenTabs      = currentNavState.hiddenTabs,
                    modifier        = Modifier.fillMaxSize()
                )
            }
        }

        // player and radio shared sizing
        val targetCompactWidth  = dockCompactWidth
        val playerLandingWidth  = fullWidth - (currentCircleSize * 2) - (dockSpacing * 2)
        val expandedPlayerWidth = fullWidth

        val currentPlayerWidth = if (p < 1f) {
            expandedPlayerWidth + (playerLandingWidth - expandedPlayerWidth) * playerMorphProgress
        } else {
            val entryProgress = (p - 1f) / 1f
            playerLandingWidth + (targetCompactWidth - playerLandingWidth) * entryProgress
        }

        // home button
        Box(
            modifier = Modifier
                .size(currentCircleSize)
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    translationX = (currentPlayerWidth / 2 + dockSpacing / 2).toPx() * radioShowFactor
                    alpha        = radioShowFactor
                    scaleX       = radioShowFactor
                    scaleY       = radioShowFactor
                    val liftDistance = dockSpacing.toPx() * (p - 1f).coerceIn(0f, 1f)
                    translationY = -liftDistance + commonDip
                }
        ) {
            HomeCircleButton(
                modifier = Modifier.fillMaxSize(),
                onClick = onHomeClick
            )
        }

        // Mini player
        Box(
            modifier = Modifier
                .height(currentCircleSize)
                .width(currentPlayerWidth)
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    val travelDistance = (baseSize + dockSpacing).toPx()
                    val liftDistance   = dockSpacing.toPx() * (p - 1f).coerceIn(0f, 1f)
                    translationY = -travelDistance * (1f - playerSlideProgress) - liftDistance + commonDip
                    translationX = -(currentCircleSize / 2 + dockSpacing / 2).toPx() * radioShowFactor
                }
        ) {
            // the fixed-width content is masked by the pill
            MorphingMiniPlayer(
                progress = playerMorphProgress,
                onClick  = onPlayerClick,
                modifier = Modifier.fillMaxSize(),
                contentWidth = fullWidth
            )
        }
    }
}
