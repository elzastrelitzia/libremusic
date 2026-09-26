package app.pulse.android.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import app.pulse.android.ui.components.themed.LocalDockHiddenCount
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pulse.android.R
import app.pulse.android.preferences.AppearancePreferences
import app.pulse.android.preferences.PlayerPreferences
import app.pulse.android.ui.screens.Route
import app.pulse.android.utils.currentLocale
import app.pulse.android.utils.findActivity
import app.pulse.android.utils.startLanguagePicker
import app.pulse.core.ui.ColorMode
import app.pulse.core.ui.ColorSource
import app.pulse.core.ui.Darkness
import app.pulse.core.ui.LocalAppearance
import app.pulse.core.ui.utils.isAtLeastAndroid13
import kotlinx.collections.immutable.toImmutableList
import app.pulse.compose.routing.RouteHandler
import app.pulse.android.ui.screens.GlobalRoutes
import app.pulse.android.ui.components.themed.Scaffold


@Route
@Composable
fun AppearanceSettings() {
    with(AppearancePreferences) {
        val (colorPalette) = LocalAppearance.current
        val context = LocalContext.current
        val isDark = isSystemInDarkTheme()

        RouteHandler {
            val dockHiddenCount = LocalDockHiddenCount.current
            DisposableEffect(Unit) {
                dockHiddenCount.value++
                onDispose { dockHiddenCount.value-- }
            }
            GlobalRoutes()

            Content {
                Scaffold(
                    key = "appearance_settings",
                    topIconButtonId = R.drawable.chevron_back,
                    onTopIconButtonClick = pop,
                    tabIndex = 0,
                    onTabChange = {},
                    tabColumnContent = {
                        tab(0, R.string.appearance, R.drawable.settings, canHide = false)
                    }
                ) {
                    SettingsCategoryScreen(
                        title = stringResource(R.string.appearance)
                    ) {
                        SettingsGroup(title = stringResource(R.string.colors)) {
            val systemDark = isSystemInDarkTheme()
            val isEffectiveDark = remember(colorMode, systemDark) {
                colorMode == ColorMode.Dark || (colorMode == ColorMode.System && systemDark)
            }

            ValueSelectorSettingsEntry(
                title = stringResource(R.string.color_source),
                selectedValue = colorSource,
                values = remember(isEffectiveDark) {
                    ColorSource.entries
                        .filter { !isEffectiveDark || it != ColorSource.Pink }
                        .toImmutableList()
                },
                onValueSelect = { colorSource = it },
                valueText = { it.nameLocalized }
            )
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.color_mode),
                selectedValue = colorMode,
                onValueSelect = { colorMode = it },
                valueText = { it.nameLocalized }
            )
            AnimatedVisibility(visible = colorMode == ColorMode.Dark || (colorMode == ColorMode.System && isDark)) {
                EnumValueSelectorSettingsEntry(
                    title = stringResource(R.string.darkness),
                    selectedValue = darkness,
                    onValueSelect = { darkness = it },
                    valueText = { it.nameLocalized }
                )
            }
        }
        SettingsGroup(title = stringResource(R.string.shapes)) {
            SwitchSettingsEntry(
                title = stringResource(R.string.compact_dock),
                text = stringResource(R.string.compact_dock_description),
                isChecked = compactDock,
                onCheckedChange = { compactDock = it }
            )
        }
        SettingsGroup(title = stringResource(R.string.text)) {
            if (isAtLeastAndroid13) SettingsEntry(
                title = stringResource(R.string.language),
                text = currentLocale()?.displayLanguage
                    ?: stringResource(R.string.color_source_default),
                onClick = {
                    context.findActivity().startLanguagePicker()
                }
            )
        }
        if (!isAtLeastAndroid13) SettingsGroup(title = stringResource(R.string.lockscreen)) {
            SwitchSettingsEntry(
                title = stringResource(R.string.show_song_cover),
                text = stringResource(R.string.show_song_cover_description),
                isChecked = isShowingThumbnailInLockscreen,
                onCheckedChange = { isShowingThumbnailInLockscreen = it }
            )
        }
        SettingsGroup(title = stringResource(R.string.player)) {
            SwitchSettingsEntry(
                title = stringResource(R.string.lyrics_keep_screen_awake),
                text = stringResource(R.string.lyrics_keep_screen_awake_description),
                isChecked = PlayerPreferences.lyricsKeepScreenAwake,
                onCheckedChange = { PlayerPreferences.lyricsKeepScreenAwake = it }
            )

            val lyricsFontSizeInitial by remember { derivedStateOf { PlayerPreferences.lyricsFontSize.toFloat() } }
            var lyricsFontSizeValue by remember(lyricsFontSizeInitial) { mutableFloatStateOf(lyricsFontSizeInitial) }

            SliderSettingsEntry(
                title = stringResource(R.string.lyrics_font_size),
                text = stringResource(R.string.lyrics_font_size_description),
                state = lyricsFontSizeValue,
                onSlide = { lyricsFontSizeValue = it },
                onSlideComplete = { PlayerPreferences.lyricsFontSize = lyricsFontSizeValue.toInt() },
                toDisplay = { stringResource(R.string.format_sp, it.toInt()) },
                range = 18f..28f,
                steps = 9
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.lyrics_show_system_bars),
                text = stringResource(R.string.lyrics_show_system_bars_description),
                isChecked = PlayerPreferences.lyricsShowSystemBars,
                onCheckedChange = { PlayerPreferences.lyricsShowSystemBars = it }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.pip),
                text = stringResource(R.string.pip_description),
                isChecked = autoPip,
                onCheckedChange = { autoPip = it }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.auto_open_player),
                text = stringResource(R.string.auto_open_player_description),
                isChecked = openPlayer,
                onCheckedChange = { openPlayer = it }
            )
        }
        SettingsGroup(title = stringResource(R.string.songs)) {
            SwitchSettingsEntry(
                title = stringResource(R.string.swipe_to_hide_song),
                text = stringResource(R.string.swipe_to_hide_song_description),
                isChecked = swipeToHideSong,
                onCheckedChange = { swipeToHideSong = it }
            )
            AnimatedVisibility(
                visible = swipeToHideSong,
                label = ""
            ) {
                SwitchSettingsEntry(
                    title = stringResource(R.string.swipe_to_hide_song_confirm),
                    text = stringResource(R.string.swipe_to_hide_song_confirm_description),
                    isChecked = swipeToHideSongConfirm,
                    onCheckedChange = { swipeToHideSongConfirm = it }
                )
            }
            SwitchSettingsEntry(
                title = stringResource(R.string.hide_explicit),
                text = stringResource(R.string.hide_explicit_description),
                isChecked = hideExplicit,
                onCheckedChange = { hideExplicit = it }
            )
        }
    }
    }
    }
    }
    }
}
val ColorSource.nameLocalized
    @Composable get() = stringResource(
        when (this) {
            ColorSource.Default -> R.string.color_source_default
            ColorSource.Dynamic -> R.string.color_source_dynamic
            ColorSource.MaterialYou -> R.string.color_source_material_you
            ColorSource.Pink -> R.string.color_source_pink
        }
    )

val ColorMode.nameLocalized
    @Composable get() = stringResource(
        when (this) {
            ColorMode.System -> R.string.color_mode_system
            ColorMode.Light -> R.string.color_mode_light
            ColorMode.Dark -> R.string.color_mode_dark
        }
    )

val Darkness.nameLocalized
    @Composable get() = stringResource(
        when (this) {
            Darkness.Normal -> R.string.darkness_normal
            Darkness.AMOLED -> R.string.darkness_amoled
            Darkness.PureBlack -> R.string.darkness_pureblack
        }
    )


