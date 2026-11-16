package app.pulse.android.ui.components

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.pulse.android.utils.AudioMediaCursor
import app.pulse.android.utils.hasPermission
import app.pulse.android.utils.mediaStoreId

@Composable
fun rememberDeviceSongDeleter(
    onDeleted: (String) -> Unit,
    onFailed: () -> Unit
): (String) -> Unit {
    val context = LocalContext.current
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    val currentOnFailed by rememberUpdatedState(onFailed)
    var pendingSongId by rememberSaveable { mutableStateOf<String?>(null) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val songId = pendingSongId ?: return@rememberLauncherForActivityResult
        pendingSongId = null
        if (result.resultCode != Activity.RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        val deleted = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            runCatching {
                songUri(songId)?.let { context.contentResolver.delete(it, null, null) } ?: 0
            }.getOrDefault(0) > 0
        } else true
        if (deleted) currentOnDeleted(songId) else currentOnFailed()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val songId = pendingSongId ?: return@rememberLauncherForActivityResult
        pendingSongId = null
        if (!granted) {
            currentOnFailed()
            return@rememberLauncherForActivityResult
        }
        val deleted = runCatching {
            songUri(songId)?.let { context.contentResolver.delete(it, null, null) } ?: 0
        }.getOrDefault(0) > 0
        if (deleted) currentOnDeleted(songId) else currentOnFailed()
    }

    return remember(context, consentLauncher, permissionLauncher) {
        delete@{ songId ->
            val uri = songUri(songId)
            if (uri == null) {
                currentOnFailed()
                return@delete
            }

            pendingSongId = songId

            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> runCatching {
                    val request = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        listOf(uri)
                    )
                    consentLauncher.launch(
                        IntentSenderRequest.Builder(request.intentSender).build()
                    )
                }.onFailure {
                    pendingSongId = null
                    currentOnFailed()
                }

                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> try {
                    val deleted =
                        context.contentResolver.delete(uri, null, null) > 0
                    pendingSongId = null
                    if (deleted) currentOnDeleted(songId) else currentOnFailed()
                } catch (e: RecoverableSecurityException) {
                    consentLauncher.launch(
                        IntentSenderRequest.Builder(
                            e.userAction.actionIntent.intentSender
                        ).build()
                    )
                } catch (t: Throwable) {
                    pendingSongId = null
                    currentOnFailed()
                }

                context.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    val deleted = runCatching {
                        context.contentResolver.delete(uri, null, null)
                    }.getOrDefault(0) > 0
                    pendingSongId = null
                    if (deleted) currentOnDeleted(songId) else currentOnFailed()
                }

                else -> permissionLauncher.launch(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }
    }
}

/** Batch delete device songs through one MediaStore delete request (API 30+) or per-file below. */
@Composable
fun rememberDeviceSongsDeleter(
    onResult: (deletedIds: List<String>, failed: Boolean) -> Unit
): (List<String>) -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    var pendingIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val ids = pendingIds
        pendingIds = emptyList()
        if (result.resultCode != Activity.RESULT_OK) {
            currentOnResult(emptyList(), true)
            return@rememberLauncherForActivityResult
        }
        currentOnResult(ids, false)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val ids = pendingIds
        pendingIds = emptyList()
        if (!granted) {
            currentOnResult(emptyList(), true)
            return@rememberLauncherForActivityResult
        }
        val deleted = deleteAll(context, ids)
        currentOnResult(deleted, deleted.size != ids.size)
    }

    return remember(context, consentLauncher, permissionLauncher) {
        delete@{ ids ->
            val uris = ids.mapNotNull { songUri(it) }
            if (uris.isEmpty()) {
                currentOnResult(emptyList(), true)
                return@delete
            }

            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    pendingIds = ids
                    runCatching {
                        val request = MediaStore.createDeleteRequest(
                            context.contentResolver,
                            uris
                        )
                        consentLauncher.launch(
                            IntentSenderRequest.Builder(request.intentSender).build()
                        )
                    }.onFailure {
                        pendingIds = emptyList()
                        currentOnResult(emptyList(), true)
                    }
                }

                context.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    val deleted = deleteAll(context, ids)
                    currentOnResult(deleted, deleted.size != ids.size)
                }

                else -> {
                    pendingIds = ids
                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }
}

private fun deleteAll(context: android.content.Context, ids: List<String>): List<String> =
    ids.filter { id ->
        runCatching {
            songUri(id)?.let { context.contentResolver.delete(it, null, null) } ?: 0
        }.getOrDefault(0) > 0
    }

private fun songUri(songId: String): Uri? =
    mediaStoreId(songId)?.let {
        ContentUris.withAppendedId(AudioMediaCursor.uri, it)
    }