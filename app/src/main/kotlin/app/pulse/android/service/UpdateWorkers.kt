package app.pulse.android.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.pulse.android.BuildConfig
import app.pulse.android.Dependencies
import app.pulse.android.preferences.DataPreferences
import app.pulse.core.data.utils.Version
import app.pulse.core.data.utils.version
import app.pulse.providers.github.GitHub
import app.pulse.providers.github.models.Release
import app.pulse.providers.github.requests.releases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.toJavaDuration

private val VERSION_NAME = BuildConfig.VERSION_NAME.substringBeforeLast("-")
private const val REPO_OWNER = "elzastrelitzia"
private const val REPO_NAME = "libremusic"
suspend fun Version.getNewerVersion(
    repoOwner: String = REPO_OWNER,
    repoName: String = REPO_NAME,
    contentType: String = "application/vnd.android.package-archive"
) = GitHub.releases(
    owner = repoOwner,
    repo = repoName
)?.mapCatching { releases ->
    releases
        .sortedByDescending { it.publishedAt }
        .firstOrNull { release ->
            !release.draft &&
                !release.preRelease &&
                release.tag.version > this &&
                release.assets.any {
                    it.contentType == contentType && it.state == Release.Asset.State.Uploaded
                }
        }
}

/**
 * The release body is written for the release page: a version line, checksums, a
 * signature link, then the git log. Keep only what belongs in the what's-new dialog.
 *
 * The raw markdown body is used, not [Release.text], because the GitHub releases
 * API never populates body_text. Markdown is shown verbatim; the lines we keep
 * carry no markup in the workflow's template.
 */
// ponytail: matches our own release body wording; returns null if the workflow body
// is reshaped, so the dialog is skipped rather than showing a raw dump.
private fun whatsNewFrom(releaseBody: String?): String? {
    val lines = releaseBody
        ?.lines()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.filterNot { line ->
            line.contains("SHA-256") ||
                line.contains("GPG") ||
                line.startsWith("Official Release of") ||
                line == "Changelog:"
        }
        ?.map { line ->
            if (!line.startsWith("-")) return@map line
            val text = line.removePrefix("-").trim()
            val prefix = listOf("android: ", "desktop: ", "ci: ").firstOrNull { text.startsWith(it) }
            "- " + (prefix?.let { text.removePrefix(it) } ?: text)
        }
        ?: return null

    // A hand-written summary is normally written after the generated bullets
    // show it first, where it is actually read.
    val (prose, bullets) = lines.partition { !it.startsWith("-") }
    return (prose + bullets).joinToString("\n").takeIf { it.isNotEmpty() }
}

/**
 * "What's new" for the version that is running, or null when that release has no
 * usable notes. Returns version to body.
 */
suspend fun whatsNewForInstalledVersion(): Pair<String, String>? =
    GitHub.releases(
        owner = REPO_OWNER,
        repo = REPO_NAME
    )?.getOrNull()
        ?.firstOrNull { it.tag.removePrefix("v") == VERSION_NAME }
        ?.let { release -> whatsNewFrom(release.markdown)?.let { VERSION_NAME to it } }

class VersionCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_TAG = "version_check_worker"

        fun executeOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<VersionCheckWorker>()
                .addTag(WORK_TAG)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_TAG}_onetime",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun upsert(context: Context, period: Duration?) = runCatching {
            val workManager = WorkManager.getInstance(context)

            if (period == null) {
                workManager.cancelAllWorkByTag(WORK_TAG)
                return@runCatching
            }

            val request = PeriodicWorkRequestBuilder<VersionCheckWorker>(period.toJavaDuration())
                .addTag(WORK_TAG)
                .setConstraints(
                    Constraints(
                        requiredNetworkType = NetworkType.CONNECTED,
                        requiresBatteryNotLow = true
                    )
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                /* uniqueWorkName = */ WORK_TAG,
                /* existingPeriodicWorkPolicy = */ ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                /* periodicWork = */ request
            )
        }.onFailure { it.printStackTrace() }
    }

    override suspend fun doWork(): Result = with(applicationContext) {
        val result = withContext(Dispatchers.IO) {
            VERSION_NAME.version.getNewerVersion().also { it?.exceptionOrNull()?.printStackTrace() }
        }

        result?.getOrNull()?.let { release ->
            val asset = release.assets.firstOrNull { it.contentType == "application/vnd.android.package-archive" }
            val url = asset?.downloadUrl?.toString() ?: release.frontendUrl.toString()

            // store pending update info for UI to show dialog
            DataPreferences.pendingUpdateVersion = release.tag.removePrefix("v")
            DataPreferences.pendingUpdateUrl = url
        }

        return when {
            result == null || result.isFailure -> Result.retry()
            result.isSuccess -> Result.success()
            else -> Result.failure()
        }
    }
}

class YtDlpUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_TAG = "ytdlp_update_worker"

        fun executeOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<YtDlpUpdateWorker>()
                .addTag(WORK_TAG)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_TAG}_onetime",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Resolve the newest nightly and record it. The dialog installs it, because
        // the update is mandatory, so the worker itself downloads nothing.
        val latest = Dependencies.ytDlpUpdateAvailable()
        if (latest != null) DataPreferences.pendingYtDlpVersion = latest
        Log.i("MusicEngine", "yt-dlp update check: latest=$latest")

        Result.success()
    }
}
