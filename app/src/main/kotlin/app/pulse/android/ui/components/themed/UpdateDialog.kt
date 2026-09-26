package app.pulse.android.ui.components.themed

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import app.pulse.android.R
import app.pulse.android.utils.medium
import app.pulse.core.ui.LocalAppearance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

private enum class DialogState {
    Prompt,
    Downloading,
    Downloaded,
    Error
}

@Composable
fun UpdateDialog(
    downloadUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (palette, typography) = LocalAppearance.current

    var state by remember { mutableStateOf(DialogState.Prompt) }
    var progress by remember { mutableFloatStateOf(0f) }
    var apkFile by remember { mutableStateOf<File?>(null) }

    Dialog(onDismissRequest = { if (state != DialogState.Downloading) onDismiss() }) {
        AppleDialogSurface {
            Column(
                modifier = Modifier.height(150.dp),
            ) {
                TitleSection(
                    state = state,
                    palette = palette,
                    typography = typography,
                )

                when (state) {
                    DialogState.Prompt -> {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            BasicText(
                                text = stringResource(R.string.update_available_description),
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = typography.xs.medium.copy(
                                    color = palette.textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                ),
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    DialogState.Downloading -> {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            BasicText(
                                text = stringResource(R.string.downloading),
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = typography.xs.medium.copy(
                                    color = palette.textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                ),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    DialogState.Downloaded -> {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            BasicText(
                                text = stringResource(R.string.install_update),
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = typography.xs.medium.copy(
                                    color = palette.textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                ),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    DialogState.Error -> {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            BasicText(
                                text = stringResource(R.string.error_message),
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = typography.s.copy(
                                    color = palette.text,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                when (state) {
                    DialogState.Prompt -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AppleDialogButton(
                                text = stringResource(R.string.later),
                                containerColor = palette.background2,
                                contentColor = palette.text,
                                modifier = Modifier.weight(1f),
                            ) { onDismiss() }
                            AppleDialogButton(
                                text = stringResource(R.string.update),
                                containerColor = palette.accent,
                                contentColor = palette.onAccent,
                                modifier = Modifier.weight(1f),
                            ) {
                                state = DialogState.Downloading
                                scope.launch {
                                    downloadApk(context, downloadUrl) { progress = it }
                                        .onSuccess { file ->
                                            apkFile = file
                                            state = DialogState.Downloaded
                                        }
                                        .onFailure { state = DialogState.Error }
                                }
                            }
                        }
                    }

                    DialogState.Downloading -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AppleDialogButton(
                                text = stringResource(R.string.later),
                                containerColor = palette.background2,
                                contentColor = palette.text,
                                modifier = Modifier.weight(1f),
                                enabled = false,
                            ) { }
                            AppleDialogProgressButton(
                                progress = progress,
                                palette = palette,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    DialogState.Downloaded -> {
                        AppleDialogButton(
                            text = stringResource(R.string.installing_update),
                            containerColor = palette.accent,
                            contentColor = palette.onAccent,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            apkFile?.let { file ->
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/vnd.android.package-archive")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                context.startActivity(intent)
                            }
                            onDismiss()
                        }
                    }

                    DialogState.Error -> {
                        AppleDialogButton(
                            text = stringResource(R.string.cancel),
                            containerColor = palette.background2,
                            contentColor = palette.text,
                            modifier = Modifier.fillMaxWidth(),
                        ) { onDismiss() }
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleSection(
    state: DialogState,
    palette: app.pulse.core.ui.ColorPalette,
    typography: app.pulse.core.ui.Typography,
) {
    when (state) {
        DialogState.Downloaded -> {
            BasicText(
                text = stringResource(R.string.update_ready),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                style = typography.s.copy(
                    color = palette.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                ),
            )
        }

        else -> {
            BasicText(
                text = stringResource(R.string.update_available),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                style = typography.s.copy(
                    color = palette.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                ),
            )
        }
    }
}

@Composable
internal fun AppleDialogProgressButton(
    progress: Float,
    palette: app.pulse.core.ui.ColorPalette,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .requiredHeight(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(palette.accent),
    ) {
        Box(modifier = Modifier.size(28.dp)) {
            Canvas(modifier = Modifier.size(28.dp)) {
                val strokeWidth = 3.5.dp.toPx()
                val halfStroke = strokeWidth / 2f
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(halfStroke, halfStroke)

                // Track
                drawArc(
                    color = palette.onAccent.copy(alpha = 0.2f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth),
                )

                // Progress
                drawArc(
                    color = palette.onAccent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth),
                )
            }
        }
    }
}

@Composable
internal fun AppleDialogButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .requiredHeight(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.5f))
            .clickable(enabled = enabled) { onClick() },
    ) {
        BasicText(
            text = text,
            style = LocalAppearance.current.typography.s.copy(
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            ),
        )
    }
}

private fun File.calculateSha256(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private suspend fun fetchChecksum(apkUrl: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val checksumUrl = apkUrl.substringBeforeLast("/") + "/checksums.txt"
        val connection = URL(checksumUrl).openConnection().apply {
            connectTimeout = 5000
            readTimeout = 5000
        }
        connection.getInputStream().bufferedReader().readText().trim()
    }.getOrNull()
}

private suspend fun downloadApk(
    context: android.content.Context,
    urlString: String,
    onProgress: (Float) -> Unit
): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
        val file = File(context.externalCacheDir ?: context.cacheDir, "updates/update.apk")
        file.parentFile?.mkdirs()

        val connection = URL(urlString).openConnection()
        val contentLength = connection.contentLengthLong
        val input = connection.getInputStream()
        val output = FileOutputStream(file)

        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalBytes = 0L

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalBytes += bytesRead
            if (contentLength > 0) {
                onProgress(totalBytes.toFloat() / contentLength)
            }
        }

        output.close()
        input.close()

        // Verification step
        val expectedHash = fetchChecksum(urlString)
        if (expectedHash != null) {
            val actualHash = file.calculateSha256()
            if (actualHash != expectedHash) {
                file.delete()
                throw IllegalStateException("Verification failed: Hash mismatch")
            }
        }

        file
    }
}
