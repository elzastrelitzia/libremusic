package app.pulse.android.preferences

import android.media.audiofx.PresetReverb
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.pulse.android.GlobalPreferencesHolder
import app.pulse.android.R

// translation target for synced lyrics; Device = the user's device/app language.
// langCode follows Google translate's target codes (zh-CN = simplified Chinese).
enum class LyricsTranslationLanguage(val langCode: String) {
    Device(""),
    English("en"),
    Spanish("es"),
    French("fr"),
    German("de"),
    Italian("it"),
    Portuguese("pt"),
    Russian("ru"),
    Japanese("ja"),
    Korean("ko"),
    SimplifiedChinese("zh-CN"),
    Arabic("ar"),
    Hindi("hi"),
    Indonesian("id"),
    Turkish("tr"),
    Dutch("nl"),
    Polish("pl"),
    Swedish("sv"),
    Thai("th"),
    Vietnamese("vi"),
    Persian("fa"),
    Ukrainian("uk"),
    Czech("cs"),
    Greek("el"),
    Hebrew("he"),
    Hungarian("hu"),
    Romanian("ro")
}

object PlayerPreferences : GlobalPreferencesHolder() {
    val isInvincibilityEnabledProperty = boolean(false)
    var isInvincibilityEnabled by isInvincibilityEnabledProperty
    val trackLoopEnabledProperty = boolean(false)
    var trackLoopEnabled by trackLoopEnabledProperty
    val queueLoopEnabledProperty = boolean(true)
    var queueLoopEnabled by queueLoopEnabledProperty
    val skipSilenceProperty = boolean(false)
    var skipSilence by skipSilenceProperty
    var crossfadeSeconds by int(6)
    val volumeNormalizationProperty = boolean(false)
    var volumeNormalization by volumeNormalizationProperty
    val volumeNormalizationBaseGainProperty = float(5.00f)
    var volumeNormalizationBaseGain by volumeNormalizationBaseGainProperty
    val bassBoostProperty = boolean(false)
    var bassBoost by bassBoostProperty
    val bassBoostLevelProperty = int(5)
    var bassBoostLevel by bassBoostLevelProperty
    val reverbProperty = enum(Reverb.None)
    var reverb by reverbProperty
    val resumePlaybackWhenDeviceConnectedProperty = boolean(false)
    var resumePlaybackWhenDeviceConnected by resumePlaybackWhenDeviceConnectedProperty
    val speedProperty = float(1f)
    var speed by speedProperty
    val pitchProperty = float(1f)
    var pitch by pitchProperty
    var minimumSilence by long(2_000_000L)
    var persistentQueue by boolean(true)
    var stopWhenClosed by boolean(false)
    var stopOnMinimumVolume by boolean(true)

    var isShowingLyrics by boolean(false)
    var isShowingSynchronizedLyrics by boolean(true)


    var lyricsKeepScreenAwake by boolean(true)
    var lyricsFontSize by int(32)
    var lyricsShowSystemBars by boolean(true)
    var lyricsTranslationLanguage by enum(LyricsTranslationLanguage.Device)
    var lyricsTranslationEnabled by boolean(false)

    var skipOnError by boolean(false)
    var handleAudioFocus by boolean(true)

    var pauseCache by boolean(false)

    val sponsorBlockEnabledProperty = boolean(false)
    var sponsorBlockEnabled by sponsorBlockEnabledProperty

    @Suppress("unused")
    enum class Reverb(
        val preset: Short,
        val displayName: @Composable () -> String
    ) {
        None(
            preset = PresetReverb.PRESET_NONE,
            displayName = { stringResource(R.string.none) }
        ),
        SmallRoom(
            preset = PresetReverb.PRESET_SMALLROOM,
            displayName = { stringResource(R.string.reverb_small_room) }
        ),
        MediumRoom(
            preset = PresetReverb.PRESET_MEDIUMROOM,
            displayName = { stringResource(R.string.reverb_medium_room) }
        ),
        LargeRoom(
            preset = PresetReverb.PRESET_LARGEROOM,
            displayName = { stringResource(R.string.reverb_large_room) }
        ),
        MediumHall(
            preset = PresetReverb.PRESET_MEDIUMHALL,
            displayName = { stringResource(R.string.reverb_medium_hall) }
        ),
        LargeHall(
            preset = PresetReverb.PRESET_LARGEHALL,
            displayName = { stringResource(R.string.reverb_large_hall) }
        ),
        Plate(
            preset = PresetReverb.PRESET_PLATE,
            displayName = { stringResource(R.string.reverb_plate) }
        )
    }
}
