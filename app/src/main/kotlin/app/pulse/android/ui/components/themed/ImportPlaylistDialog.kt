package app.pulse.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.pulse.android.utils.PlaylistImporter
import app.pulse.android.utils.medium
import app.pulse.core.ui.LocalAppearance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportPlaylistDialog(
    onDismiss: () -> Unit,
) {
    val (palette, typography) = LocalAppearance.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var currentTrack by remember { mutableStateOf<String?>(null) }
    var importJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        AppleDialogSurface {
            BasicText(
                text = "Import Playlist",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                style = typography.s.copy(
                    color = palette.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                ),
            )

            Spacer(Modifier.height(12.dp))

            if (!isLoading) {
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    hintText = "Paste playlist URL",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.background2, RoundedCornerShape(50))
                        .border(1.dp, palette.text.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )

                status?.let {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.background2, RoundedCornerShape(50))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        BasicText(
                            text = it,
                            style = typography.xs.medium.copy(
                                color = palette.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            ),
                        )
                    }
                }
            } else {
                val subStyle = typography.xs.medium.copy(
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                BasicText(
                    text = "Importing playlist...",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = subStyle,
                )
                currentTrack?.let { track ->
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        text = track,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = subStyle.copy(fontSize = 11.sp),
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isFinished) {
                AppleDialogButton(
                    text = "Close",
                    containerColor = palette.accent,
                    contentColor = palette.onAccent,
                    modifier = Modifier.fillMaxWidth(),
                ) { onDismiss() }
            } else if (!isLoading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppleDialogButton(
                        text = "Cancel",
                        containerColor = palette.background2,
                        contentColor = palette.text,
                        modifier = Modifier.weight(1f),
                    ) { onDismiss() }
                    AppleDialogButton(
                        text = "Import",
                        containerColor = palette.accent,
                        contentColor = palette.onAccent,
                        modifier = Modifier.weight(1f),
                        enabled = url.isNotBlank(),
                    ) {
                        isLoading = true
                        status = null
                        importJob = scope.launch(Dispatchers.IO) {
                            try {
                                val result = PlaylistImporter.importFromUrl(
                                    url = url,
                                    onProgress = { current, total, track ->
                                        withContext(Dispatchers.Main) {
                                            currentTrack = "$current/$total — $track"
                                        }
                                    }
                                ).getOrThrow()

                                val stored = PlaylistImporter.persistImport(result)
                                val duplicates = result.resolvedTracks.size - stored

                                withContext(Dispatchers.Main) {
                                    val skipped = result.unresolvedTracks
                                    val dupNote = if (duplicates > 0) " (${duplicates} duplicates collapsed)" else ""
                                    status = if (skipped.isEmpty()) {
                                        "Imported $stored tracks$dupNote"
                                    } else {
                                        val names = skipped.take(5).joinToString(", ") { it.title }
                                        val more = if (skipped.size > 5) ", …" else ""
                                        "Imported $stored tracks$dupNote, skipped ${skipped.size}:\n$names$more"
                                    }
                                    isLoading = false
                                    isFinished = true
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    status = e.message ?: "Import failed"
                                    isLoading = false
                                    isFinished = true
                                }
                            }
                        }
                    }
                }
            } else {
                AppleDialogButton(
                    text = "Cancel",
                    containerColor = palette.background2,
                    contentColor = palette.text,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    importJob?.cancel()
                    isLoading = false
                    isFinished = true
                    status = null
                }
            }
        }
    }
}
