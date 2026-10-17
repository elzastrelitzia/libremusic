package app.pulse.android.preferences

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.pulse.android.BuildConfig
import app.pulse.android.GlobalPreferencesHolder
import app.pulse.android.R
import app.pulse.core.data.enums.CoilDiskCacheSize
import app.pulse.core.data.enums.ExoPlayerDiskCacheSize
import app.pulse.providers.innertube.Innertube
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

object DataPreferences : GlobalPreferencesHolder() {
    var coilDiskCacheMaxSize by enum(CoilDiskCacheSize.`2GB`)
    var exoPlayerDiskCacheMaxSize by enum(ExoPlayerDiskCacheSize.`4GB`)

    var pauseHistory by boolean(false)
    var pausePlaytime by boolean(false)
    var pauseSearchHistory by boolean(false)
    val topListLengthProperty = int(50)
    var topListLength by topListLengthProperty
    val topListPeriodProperty = enum(TopListPeriod.AllTime)
    var topListPeriod by topListPeriodProperty
    var quickPicksSource by enum(QuickPicksSource.Trending)
    val homeFeedCacheDaysProperty = int(7)
    var homeFeedCacheDays by homeFeedCacheDaysProperty
    var versionCheckPeriod by enum(VersionCheckPeriod.Daily)
    var ytDlpUpdate by enum(VersionCheckPeriod.Daily)
    var pendingYtDlpVersion by string("")
    var autoSyncPlaylists by boolean(true)
    var pendingUpdateVersion by string("")
    var pendingUpdateUrl by string("")
    // defaults to the running build so a fresh install never counts as an update
    var lastSeenVersionCode by int(BuildConfig.VERSION_CODE)
    var batteryPromptShown by boolean(false)

    enum class TopListPeriod(
        val displayName: @Composable () -> String,
        val duration: Duration? = null
    ) {
        PastDay(displayName = { stringResource(R.string.past_24_hours) }, duration = 1.days),
        PastWeek(displayName = { stringResource(R.string.past_week) }, duration = 7.days),
        PastMonth(displayName = { stringResource(R.string.past_month) }, duration = 30.days),
        PastYear(displayName = { stringResource(R.string.past_year) }, 365.days),
        AllTime(displayName = { stringResource(R.string.all_time) })
    }

    enum class QuickPicksSource(val displayName: @Composable () -> String) {
        Trending(displayName = { stringResource(R.string.trending) }),
        LastInteraction(displayName = { stringResource(R.string.last_interaction) })
    }

    @Suppress("unused")
    enum class VersionCheckPeriod(
        val displayName: @Composable () -> String,
        val period: Duration?
    ) {
        Off(displayName = { stringResource(R.string.off_text) }, period = null),
        Hourly(displayName = { stringResource(R.string.hourly) }, period = 1.hours),
        Daily(displayName = { stringResource(R.string.daily) }, period = 1.days),
        Weekly(displayName = { stringResource(R.string.weekly) }, period = 7.days)
    }
}
