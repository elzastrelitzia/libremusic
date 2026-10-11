package app.pulse.compose.routing

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

enum class RouteTransition { Slide, Fade }

private const val SlideMillis = 250

val defaultStacking = ContentTransform(
    targetContentEnter = slideInHorizontally(tween(SlideMillis)) { it },
    initialContentExit = slideOutHorizontally(tween(SlideMillis)) { -it },
    targetContentZIndex = 1f
)

val defaultUnstacking = ContentTransform(
    targetContentEnter = slideInHorizontally(tween(SlideMillis)) { -it },
    initialContentExit = slideOutHorizontally(tween(SlideMillis)) { it },
    targetContentZIndex = 0f
)

val fadeStacking = ContentTransform(
    targetContentEnter = fadeIn(),
    initialContentExit = fadeOut(),
    targetContentZIndex = 1f
)

val fadeUnstacking = ContentTransform(
    targetContentEnter = EnterTransition.None,
    initialContentExit = fadeOut(),
    targetContentZIndex = 0f
)

val RouteTransition.stacking: ContentTransform
    get() = if (this == RouteTransition.Fade) fadeStacking else defaultStacking

val RouteTransition.unstacking: ContentTransform
    get() = if (this == RouteTransition.Fade) fadeUnstacking else defaultUnstacking

val TransitionScope<*>.isStacking: Boolean
    get() = initialState == null && targetState != null

val TransitionScope<*>.isUnstacking: Boolean
    get() = initialState != null && targetState == null

val TransitionScope<*>.isStill: Boolean
    get() = initialState == null && targetState == null
