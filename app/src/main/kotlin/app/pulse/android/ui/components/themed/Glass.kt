package app.pulse.android.ui.components.themed

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pulse.core.ui.ColorPalette
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.material3.material3

/**
 * tuning surface for glass.
 */
data class LiquidGlassTokens(
    val blurRadius: Dp = 2.dp,
    val backgroundAlpha: Float = 0.3f,
    val tintAlpha: Float = 0.5f,
    val specularIntensity: Float = 0f,
    val edgeSoftness: Dp = 0.dp,
    val edgeShadow: Color = Color.Transparent,
)

@Composable
fun liquidGlassStyle(
    colorPalette: ColorPalette,
    tokens: LiquidGlassTokens = LiquidGlassTokens(),
): GlassStyle = GlassStyle.clear.material3().then {
    backgroundColor(colorPalette.background1.copy(alpha = tokens.backgroundAlpha))
    tint(colorPalette.background1.copy(alpha = tokens.tintAlpha))
    specularIntensity(tokens.specularIntensity)
    edgeSoftness(tokens.edgeSoftness)
    edgeShadow(tokens.edgeShadow)
    optics { copy(blurRadius = OpticalSizeValue.Fixed(tokens.blurRadius)) }
}
