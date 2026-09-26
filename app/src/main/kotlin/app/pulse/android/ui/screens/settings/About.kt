package app.pulse.android.ui.screens.settings

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import app.pulse.android.ui.components.themed.LocalDockHiddenCount
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.pulse.android.BuildConfig
import app.pulse.android.R
import app.pulse.android.preferences.DataPreferences
import app.pulse.android.service.ServiceNotifications
import app.pulse.android.ui.components.themed.CircularProgressIndicator
import app.pulse.android.ui.components.themed.DefaultDialog
import app.pulse.android.ui.components.themed.SecondaryTextButton
import app.pulse.android.ui.screens.Route

import app.pulse.android.utils.bold
import app.pulse.android.utils.center
import app.pulse.android.utils.hasPermission
import app.pulse.android.utils.pendingIntent
import app.pulse.android.utils.semiBold
import app.pulse.core.data.utils.Version
import app.pulse.core.data.utils.version
import app.pulse.core.ui.LocalAppearance
import app.pulse.core.ui.utils.isAtLeastAndroid13
import app.pulse.core.ui.utils.isCompositionLaunched
import app.pulse.providers.github.GitHub
import app.pulse.providers.github.models.Release
import app.pulse.providers.github.requests.releases
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import app.pulse.compose.routing.RouteHandler
import app.pulse.android.ui.screens.GlobalRoutes
import app.pulse.android.ui.components.themed.Scaffold

private val VERSION_NAME = BuildConfig.VERSION_NAME.substringBeforeLast("-")
private const val REPO_OWNER = "khuza08"
private const val REPO_NAME = "pulse"

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private val permission = Manifest.permission.POST_NOTIFICATIONS

@Route
@Composable
fun About() {
    RouteHandler {
            val dockHiddenCount = LocalDockHiddenCount.current
            DisposableEffect(Unit) {
                dockHiddenCount.value++
                onDispose { dockHiddenCount.value-- }
            }
        GlobalRoutes()

        Content {
            Scaffold(
                key = "about",
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = 0,
                onTabChange = {},
                tabColumnContent = {
                    tab(0, R.string.about, R.drawable.settings, canHide = false)
                }
            ) {
                SettingsCategoryScreen(
                    title = stringResource(R.string.about),
                    description = stringResource(
                        R.string.format_version_credits,
                        VERSION_NAME
                    )
                ) {
                    val (_, typography) = LocalAppearance.current
                    val uriHandler = LocalUriHandler.current
                    val context = LocalContext.current

                    var hasPermission by remember(isCompositionLaunched()) {
                        mutableStateOf(
                            if (isAtLeastAndroid13) context.applicationContext.hasPermission(permission)
                            else true
                        )
                    }

                    val launcher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = { hasPermission = it }
                    )

                    SettingsGroup(title = stringResource(R.string.social)) {
                        SettingsEntry(
                            title = stringResource(R.string.github),
                            text = stringResource(R.string.view_source),
                            onClick = {
                                uriHandler.openUri("https://github.com/$REPO_OWNER/$REPO_NAME")
                            }
                        )
                    }

                    SettingsGroup(title = stringResource(R.string.contact)) {
                        SettingsEntry(
                            title = stringResource(R.string.report_bug),
                            text = stringResource(R.string.report_bug_description),
                            onClick = {
                                uriHandler.openUri(
                                    @Suppress("MaximumLineLength")
                                    "https://github.com/$REPO_OWNER/$REPO_NAME/issues/new?assignees=&labels=bug&template=bug_report.yaml"
                                )
                            }
                        )

                        SettingsEntry(
                            title = stringResource(R.string.request_feature),
                            text = stringResource(R.string.redirect_github),
                            onClick = {
                                uriHandler.openUri(
                                    @Suppress("MaximumLineLength")
                                    "https://github.com/$REPO_OWNER/$REPO_NAME/issues/new?assignees=&labels=enhancement&template=feature_request.md"
                                )
                            }
                        )
                    }


                    SettingsGroup(title = stringResource(R.string.version)) {
                        SettingsEntry(
                            title = stringResource(R.string.check_new_version),
                            text = stringResource(R.string.current_version, VERSION_NAME),
                            onClick = {
                                app.pulse.android.service.VersionCheckWorker.executeOneTime(context.applicationContext)
                            }
                        )

                        EnumValueSelectorSettingsEntry(
                            title = stringResource(R.string.version_check),
                            selectedValue = DataPreferences.versionCheckPeriod,
                            onValueSelect = onSelect@{
                                DataPreferences.versionCheckPeriod = it
                                if (isAtLeastAndroid13 && it.period != null && !hasPermission)
                                    launcher.launch(permission)

                                app.pulse.android.service.VersionCheckWorker.upsert(context.applicationContext, it.period)
                            },
                            valueText = { it.displayName() }
                        )
                    }
                }
            }
        }
    }
}
