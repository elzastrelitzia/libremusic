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

data class NavigationState(
    val tabs: ImmutableList<Tab>,
    val tabIndex: Int,
    val onTabChange: (Int) -> Unit,
    val hiddenTabs: ImmutableList<String>
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

@Composable
fun Scaffold(
    key: String,
    topIconButtonId: Int,
    onTopIconButtonClick: () -> Unit,
    tabIndex: Int,
    onTabChange: (Int) -> Unit,
    tabColumnContent: TabsBuilder.() -> Unit,
    modifier: Modifier = Modifier,
    tabsEditingTitle: String = stringResource(R.string.tabs),
    isGlobalNav: Boolean = false,
    content: @Composable AnimatedVisibilityScope.(Int) -> Unit
) {
    val (colorPalette) = LocalAppearance.current
    var hiddenTabs by UIStatePreferences.mutableTabStateOf(key)
    val tabs = TabsBuilder.rememberTabs(tabColumnContent)
    val isLandscape = isLandscape
    val globalNavigationState = LocalNavigationState.current

    if (isGlobalNav) {
        DisposableEffect(tabs, tabIndex, onTabChange, hiddenTabs) {
            val previousState = globalNavigationState.value
            globalNavigationState.value = NavigationState(
                tabs = tabs,
                tabIndex = tabIndex,
                onTabChange = onTabChange,
                hiddenTabs = hiddenTabs
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
