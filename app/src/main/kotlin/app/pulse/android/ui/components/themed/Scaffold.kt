package app.pulse.android.ui.components.themed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Left
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Right
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import app.pulse.android.R
import app.pulse.android.preferences.UIStatePreferences
import app.pulse.core.ui.LocalAppearance
import app.pulse.core.ui.utils.isLandscape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.pulse.android.utils.color
import app.pulse.android.utils.semiBold
import app.pulse.compose.routing.LocalPredictiveBackInProgress

data class NavigationState(
    val tabs: ImmutableList<Tab>,
    val tabIndex: Int,
    val onTabChange: (Int) -> Unit,
    val hiddenTabs: ImmutableList<String>,
    val onAction: (String) -> Unit
)

val LocalNavigationState = staticCompositionLocalOf<MutableState<NavigationState?>> {
    mutableStateOf(null)
}

val LocalDockHiddenCount = staticCompositionLocalOf<MutableState<Int>> {
    mutableStateOf(0)
}

val LocalDockScrolled = staticCompositionLocalOf<MutableState<Boolean>> {
    mutableStateOf(false)
}

/**
 * Number of composed pushed screens that show the dock, so its round button becomes a back
 * arrow that pops one screen rather than a home button that unwinds to QuickPicks. Search
 * result does not register and keeps the home button. **/

val LocalDockBackArrow = staticCompositionLocalOf<MutableState<Int>> {
    mutableStateOf(0)
}

@Composable
fun Scaffold(
    key: String,
    topIconButtonId: Int,
    onTopIconButtonClick: () -> Unit,
    tabIndex: Int,
    onTabChange: (Int) -> Unit,
    onAction: (String) -> Unit = {},
    tabColumnContent: TabsBuilder.() -> Unit,
    modifier: Modifier = Modifier,
    tabsEditingTitle: String = stringResource(R.string.tabs),
    isGlobalNav: Boolean = false,
    dockBackArrow: Boolean = true,
    content: @Composable AnimatedVisibilityScope.(Int) -> Unit
) {
    val (colorPalette) = LocalAppearance.current
    var hiddenTabs by UIStatePreferences.mutableTabStateOf(key)
    val tabs = TabsBuilder.rememberTabs(tabColumnContent)
    val isLandscape = isLandscape
    val globalNavigationState = LocalNavigationState.current

    // Read through rememberUpdatedState so the publish effect's keys stay stable. The effect's
    // onDispose restores the previous state, so a re-instantiated lambda in the key list would
    // briefly publish a stale NavigationState, the dock's sub-page phase.
    val currentOnAction by rememberUpdatedState(onAction)

    // A pushed screen builds its own Scaffold, while the home tab is the only one that
    // passes isGlobalNav. So this is exactly the set of screens that should show the dock's
    // back arrow instead of its home button. Download banners and other non-dock screens
    // that also build a Scaffold do not reach the dock, which the arrow only relabels.
    if (!isGlobalNav && dockBackArrow) {
        val backArrow = LocalDockBackArrow.current
        DisposableEffect(Unit) {
            backArrow.value++
            onDispose { backArrow.value-- }
        }
    }

    // A predictive back gesture composes the screen it reveals as the seek target,
    // so a half swipe back to Home would hand this screen's tabs to the dock while
    // the user is still on the screen being popped. Only the screen being seen
    // should drive the dock.
    if (isGlobalNav && !LocalPredictiveBackInProgress.current) {
        DisposableEffect(tabs, tabIndex, onTabChange, hiddenTabs) {
            val previousState = globalNavigationState.value
            globalNavigationState.value = NavigationState(
                tabs = tabs,
                tabIndex = tabIndex,
                onTabChange = onTabChange,
                hiddenTabs = hiddenTabs,
                onAction = currentOnAction
            )
            onDispose {
                globalNavigationState.value = previousState
            }
        }
    }

    val dockScrolled = LocalDockScrolled.current
    LaunchedEffect(tabIndex) {
        if (isGlobalNav) {
            dockScrolled.value = false
        }
    }

    Row(
        modifier = modifier
            .background(colorPalette.background0)
            .fillMaxSize()
    ) {
            if (isLandscape) {
                NavigationRail(
                    topIconButtonId = topIconButtonId,
                    onTopIconButtonClick = onTopIconButtonClick,
                    tabIndex = tabIndex,
                    onTabIndexChange = onTabChange,
                    onAction = currentOnAction,
                    hiddenTabs = hiddenTabs,
                    setHiddenTabs = { hiddenTabs = it.toImmutableList() },
                    tabsEditingTitle = tabsEditingTitle,
                    content = tabColumnContent
                )
            }
            Column(modifier = Modifier.weight(1f)) {

                AnimatedContent(
                    targetState = tabIndex,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        val slideDirection = if (targetState > initialState) Left else Right
                        val animationSpec = spring(
                            dampingRatio = 0.9f,
                            stiffness = Spring.StiffnessLow,
                            visibilityThreshold = IntOffset.VisibilityThreshold
                        )

                        ContentTransform(
                            targetContentEnter = slideIntoContainer(slideDirection, animationSpec),
                            initialContentExit = slideOutOfContainer(slideDirection, animationSpec),
                            sizeTransform = null
                        )
                    },
                    content = content,
                    label = ""
                )
            }
        }
}
