package app.pulse.desktop.ui.utils

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.pulse.core.data.utils.AppDirs
import java.io.File
import java.net.URL
import java.security.MessageDigest

private val imageCacheDir = AppDirs.images
private const val DEFAULT_THUMB_SIZE = 1080

/**
 * Transform Google-hosted thumbnail URLs to request a specific pixel size.
 * Google CDN caps at original upload resolution.
 * Supports both exact-starts-with and contains fuzzy matching (like Android).
 */
private fun highResUrl(original: String, requestedSize: Int? = null): String {
    val size = requestedSize ?: DEFAULT_THUMB_SIZE
    return when {
        original.startsWith("https://lh3.googleusercontent.com") ->
            "${original.substringBeforeLast('=')}=w${size}-h${size}-p-rj-nu"
        original.startsWith("https://yt3.ggpht.com") ->
            "${original.substringBeforeLast('=')}=s${size}-p-rj-nu"
        original.contains("googleusercontent.com") || original.contains("ggpht.com") -> {
            val baseUrl = if (original.contains("=")) original.substringBeforeLast('=') else original
            if (original.contains("googleusercontent.com")) {
                "$baseUrl=w${size}-h${size}-p-rj-nu"
            } else {
                "$baseUrl=s${size}-p-rj-nu"
            }
        }
        else -> original
    }
}

/**
 * Load image from disk cache or download and cache it.
 * @param requestedSize optional pixel size to request from Google CDN.
 *   Pass the display size in pixels (dp × density) for optimal quality.
 *   If null, defaults to 1080px.
 */
@Composable
fun NetworkImage(
    url: String,
    modifier: Modifier = Modifier,
    requestedSize: Int? = null
) {
    val hiRes = highResUrl(url, requestedSize)
    var bitmap by remember(hiRes) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(hiRes) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                val cacheFile = cacheFileFor(hiRes)
                if (cacheFile.exists()) {
                    cacheFile.inputStream().buffered().use { loadImageBitmap(it) }
                } else {
                    imageCacheDir.mkdirs()
                    val bytes = URL(hiRes).openStream().buffered().use { it.readBytes() }
                    cacheFile.outputStream().use { it.write(bytes) }
                    loadImageBitmap(bytes.inputStream().buffered())
                }
            } catch (_: Exception) { null }
        }
    }
    bitmap?.let {
        Image(
            painter = BitmapPainter(it),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

private fun cacheFileFor(url: String): File {
    val hash = url.hashCode().toUInt().toString(16)
    return File(imageCacheDir, hash)
}
