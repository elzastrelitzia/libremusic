package app.pulse.android.ui.components.themed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import app.pulse.core.ui.LocalAppearance

@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    animateProgress: Boolean = progress != null,
    strokeCap: StrokeCap? = null,
    color: Color = LocalAppearance.current.colorPalette.accent,
    trackColor: Color = LocalAppearance.current.colorPalette.background1
) {
    val (colorPalette) = LocalAppearance.current

    if (progress == null) androidx.compose.material3.CircularProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        strokeCap = strokeCap ?: ProgressIndicatorDefaults.CircularIndeterminateStrokeCap
    ) else {
        val animatedProgress by (
            if (animateProgress) animateFloatAsState(targetValue = progress)
            else remember { derivedStateOf { progress } }
            )

        androidx.compose.material3.CircularProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            strokeCap = strokeCap ?: ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
            progress = { animatedProgress }
        )
    }
}

@Composable
fun LinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    animateProgress: Boolean = progress != null,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
    color: Color = LocalAppearance.current.colorPalette.accent
) {
    val (colorPalette) = LocalAppearance.current

    if (progress == null) androidx.compose.material3.LinearProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = colorPalette.background1,
        strokeCap = strokeCap
    ) else {
        val animatedProgress by (
            if (animateProgress) animateFloatAsState(targetValue = progress)
            else remember { derivedStateOf { progress } }
            )

        androidx.compose.material3.LinearProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = colorPalette.background1,
            strokeCap = strokeCap,
            progress = { animatedProgress }
        )
    }
}
