package app.pulse.android.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pulse.android.Database
import app.pulse.android.R
import app.pulse.core.data.models.Song
import app.pulse.core.data.models.toEntity
import app.pulse.core.data.utils.formatDuration
import app.pulse.android.preferences.OrderPreferences
import app.pulse.android.service.LOCAL_KEY_PREFIX
import app.pulse.android.transaction
import app.pulse.android.ui.components.themed.SecondaryTextButton
import app.pulse.android.ui.screens.Route
import app.pulse.android.utils.AudioMediaCursor
import app.pulse.android.utils.hasPermission
import app.pulse.android.utils.medium
import app.pulse.core.ui.LocalAppearance
import app.pulse.core.ui.utils.isAtLeastAndroid13
import app.pulse.core.ui.utils.isCompositionLaunched
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.milliseconds

private val permission = if (isAtLeastAndroid13) Manifest.permission.READ_MEDIA_AUDIO
else Manifest.permission.READ_EXTERNAL_STORAGE

@Route
@Composable
fun HomeLocalSongs(onSearchClick: () -> Unit) = with(OrderPreferences) {
    val context = LocalContext.current
    val (_, typography) = LocalAppearance.current

    var hasPermission by remember(isCompositionLaunched()) {
        mutableStateOf(context.applicationContext.hasPermission(permission))
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { hasPermission = it }
    )

    LaunchedEffect(hasPermission) {
        if (hasPermission) context.musicFilesAsFlow().collect()
    }

    if (hasPermission) HomeSongs(
        onSearchClick = onSearchClick,
        songProvider = {
            Database.songs(
                sortBy = localSongSortBy,
                sortOrder = localSongSortOrder,
                isLocal = true
            ).map { songs -> songs.filter { it.durationText != "0:00" } }
        },
        sortBy = localSongSortBy,
        setSortBy = { localSongSortBy = it },
        sortOrder = localSongSortOrder,
        setSortOrder = { localSongSortOrder = it },
        title = stringResource(R.string.local),
        persistTag = "home/localSongs/songs"
    ) else {
        LaunchedEffect(Unit) { launcher.launch(permission) }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicText(
                text = stringResource(R.string.media_permission_declined),
                modifier = Modifier.fillMaxWidth(0.75f),
                style = typography.m.medium
            )
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryTextButton(
                text = stringResource(R.string.open_settings),
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                }
            )
        }
    }
}

fun Context.musicFilesAsFlow(): Flow<List<Song>> = callbackFlow {
    val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            trySend(Unit)
        }
    }

    contentResolver.registerContentObserver(AudioMediaCursor.uri, true, observer)
    trySend(Unit)

    awaitClose { contentResolver.unregisterContentObserver(observer) }
}
    .conflate()
    .mapNotNull { scanMusicFiles() }
    .flowOn(Dispatchers.IO)
    .distinctUntilChanged()
    .onEach { songs -> transaction { songs.forEach { Database.insert(it.toEntity()) } } }

private fun Context.scanMusicFiles(): List<Song>? = AudioMediaCursor.query(contentResolver) {
    buildList {
        while (next()) {
            if (!isMusic || duration == 0) continue
            add(
                Song(
                    id = "$LOCAL_KEY_PREFIX$id",
                    title = name,
                    artistsText = artist,
                    durationText = formatDuration(duration.milliseconds),
                    thumbnailUrl = albumUri.toString()
                )
            )
        }
    }
}
