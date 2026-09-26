package app.pulse.compose.routing

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

val defaultStacking = ContentTransform(
    initialContentExit = fadeOut(),
    targetContentEnter = fadeIn(),
    targetContentZIndex = 1f
)

val defaultUnstacking = ContentTransform(
    initialContentExit = fadeOut(),
    targetContentEnter = EnterTransition.None,
    targetContentZIndex = 0f
)

val TransitionScope<*>.isStacking: Boolean
    get() = initialState == null && targetState != null

val TransitionScope<*>.isUnstacking: Boolean
    get() = initialState != null && targetState == null

val TransitionScope<*>.isStill: Boolean
    get() = initialState == null && targetState == null
