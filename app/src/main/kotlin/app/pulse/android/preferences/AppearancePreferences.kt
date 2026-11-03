package app.pulse.android.preferences

import android.os.Build
import app.pulse.android.GlobalPreferencesHolder
import app.pulse.android.preferences.OldPreferences.ColorPaletteMode
import app.pulse.android.preferences.OldPreferences.ColorPaletteName
import app.pulse.core.ui.ColorMode
import app.pulse.core.ui.ColorSource
import app.pulse.core.ui.Darkness

object AppearancePreferences : GlobalPreferencesHolder() {
    var colorSource by enum(ColorSource.Dynamic)

    var colorMode by enum(
        when (OldPreferences.oldColorPaletteMode) {
            ColorPaletteMode.Light -> ColorMode.Light
            ColorPaletteMode.Dark -> ColorMode.Dark
            ColorPaletteMode.System -> ColorMode.System
        }
    )
    var darkness by enum(
        when (OldPreferences.oldColorPaletteName) {
            ColorPaletteName.Default, ColorPaletteName.Dynamic, ColorPaletteName.MaterialYou -> Darkness.Normal
            ColorPaletteName.PureBlack -> Darkness.PureBlack
            ColorPaletteName.AMOLED -> Darkness.AMOLED
        }
    )
    val isShowingThumbnailInLockscreenProperty = boolean(true)
    var isShowingThumbnailInLockscreen by isShowingThumbnailInLockscreenProperty
    var swipeToHideSong by boolean(false)
    var swipeToHideSongConfirm by boolean(true)
    var maxThumbnailSize by int(1920)
    var hideExplicit by boolean(false)
    var autoPip by boolean(false)
    var openPlayer by boolean(true)
    var compactDock by boolean(false)
    var glassDockBlur by boolean(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
}
