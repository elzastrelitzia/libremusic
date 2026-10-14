package app.pulse.android.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

private const val INSTAGRAM_PACKAGE = "com.instagram.android"
private const val CARD_WIDTH = 1080
private const val CARD_HEIGHT = 1920

// The song menu is dismissed before the render
// finishes, which cancels the menu's own rememberCoroutineScope.
private val storyScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/**
 * Render a story card for [mediaItem] and hand it to Instagram Stories.
 */
fun shareToInstagramStory(context: Context, mediaItem: MediaItem) {
    storyScope.launch {
        runCatching { share(context, mediaItem) }
            .onFailure { Log.e("InstagramStory", "story share failed", it) }
    }
}

private suspend fun share(context: Context, mediaItem: MediaItem) {
    val uri = renderStoryCard(context, mediaItem)

    val stories = Intent("com.instagram.share.ADD_TO_STORY").apply {
        setPackage(INSTAGRAM_PACKAGE)
        setDataAndType(uri, "image/png")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.grantUriPermission(INSTAGRAM_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

    // also covers Android 11+ package-visibility filtering, where
    // resolveActivity() would report Instagram as absent.
    try {
        context.startActivity(stories)
    } catch (_: ActivityNotFoundException) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(fallback, null))
    }
}

private suspend fun renderStoryCard(context: Context, mediaItem: MediaItem): Uri =
    withContext(Dispatchers.IO) {
        val density = context.resources.displayMetrics.density
        val card = drawCard(
            art = loadArtwork(context, mediaItem),
            title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
            artist = mediaItem.mediaMetadata.artist?.toString().orEmpty(),
            blurRadius = 32f * density,
        )

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "story.png")
        FileOutputStream(file).use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

private suspend fun loadArtwork(context: Context, mediaItem: MediaItem): Bitmap? =
    mediaItem.mediaMetadata.artworkUri
        ?.thumbnail(CARD_WIDTH)
        ?.let { uri ->
            runCatching {
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(uri)
                        .size(CARD_WIDTH)
                        .allowHardware(false)
                        .build()
                )
                (result as? SuccessResult)?.image?.toBitmap()
            }.getOrNull()
        }

private fun drawCard(art: Bitmap?, title: String, artist: String, blurRadius: Float): Bitmap {
    val card = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(card)

    if (art != null) {
        val cover = RectF(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat())
        val cropped = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(cropped).drawBitmap(art, null, coverRect(art, cover), filterPaint())
        canvas.drawBitmap(blur(cropped, blurRadius), 0f, 0f, null)
        canvas.drawColor(Color.argb(128, 0, 0, 0)) // dim ~0.5
    } else {
        canvas.drawColor(Color.BLACK)
    }

    val centerX = CARD_WIDTH / 2f
    val artSize = CARD_WIDTH * 0.62f
    val artTop = CARD_HEIGHT * 0.24f

    if (art != null) {
        val rect = RectF(centerX - artSize / 2f, artTop, centerX + artSize / 2f, artTop + artSize)
        val corner = artSize * 0.06f
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, corner, corner, Path.Direction.CW) })
        canvas.drawBitmap(art, null, coverRect(art, rect), filterPaint())
        canvas.restore()
    }

    val maxTextWidth = CARD_WIDTH * 0.82f

    val titlePaint = textPaint(Color.WHITE, 58f, Typeface.DEFAULT_BOLD)
    val titleY = artTop + artSize + 100f
    canvas.drawText(ellipsize(title, titlePaint, maxTextWidth), centerX, titleY, titlePaint)

    val artistPaint = textPaint(Color.argb(200, 255, 255, 255), 44f)
    canvas.drawText(ellipsize(artist, artistPaint, maxTextWidth), centerX, titleY + 70f, artistPaint)

    val footerPaint = textPaint(Color.argb(150, 255, 255, 255), 38f)
    canvas.drawText("libremusic", centerX, CARD_HEIGHT - 140f, footerPaint)

    return card
}

private fun coverRect(src: Bitmap, dst: RectF): RectF {
    val scale = maxOf(dst.width() / src.width, dst.height() / src.height)
    val width = src.width * scale
    val height = src.height * scale
    val left = dst.left + (dst.width() - width) / 2f
    val top = dst.top + (dst.height() - height) / 2f
    return RectF(left, top, left + width, top + height)
}

private const val MAX_BLUR_RADIUS = 160

private fun blur(src: Bitmap, radiusPx: Float): Bitmap {
    // radiusPx is the blur radius in pixels, so the knob
    // moves one pixel at a time instead of in coarse steps.
    val radius = radiusPx.roundToInt().coerceIn(0, MAX_BLUR_RADIUS)
    if (radius == 0) return src
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    boxBlur(out, radius)
    return out
}

private fun boxBlur(bitmap: Bitmap, radius: Int) {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val buffer = IntArray(width * height)
    repeat(2) {
        blurPass(pixels, buffer, width, height, radius, horizontal = true)
        blurPass(buffer, pixels, width, height, radius, horizontal = false)
    }
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
}

private fun blurPass(
    src: IntArray,
    dst: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
) {
    val divisor = 2 * radius + 1
    val outer = if (horizontal) height else width
    val inner = if (horizontal) width else height
    val step = if (horizontal) 1 else width
    val rowStep = if (horizontal) width else 1
    for (o in 0 until outer) {
        val base = o * rowStep
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (k in -radius..radius) {
            val pixel = src[base + k.coerceIn(0, inner - 1) * step]
            a += (pixel ushr 24) and 0xFF
            r += (pixel ushr 16) and 0xFF
            g += (pixel ushr 8) and 0xFF
            b += pixel and 0xFF
        }
        dst[base] =
            ((a / divisor) shl 24) or ((r / divisor) shl 16) or ((g / divisor) shl 8) or (b / divisor)

        for (i in 1 until inner) {
            val entering = src[base + (i + radius).coerceAtMost(inner - 1) * step]
            val leaving = src[base + (i - radius - 1).coerceAtLeast(0) * step]
            a += ((entering ushr 24) and 0xFF) - ((leaving ushr 24) and 0xFF)
            r += ((entering ushr 16) and 0xFF) - ((leaving ushr 16) and 0xFF)
            g += ((entering ushr 8) and 0xFF) - ((leaving ushr 8) and 0xFF)
            b += (entering and 0xFF) - (leaving and 0xFF)
            dst[base + i * step] =
                ((a / divisor) shl 24) or ((r / divisor) shl 16) or ((g / divisor) shl 8) or (b / divisor)
        }
    }
}

private fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String =
    TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()

private fun filterPaint() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

private fun textPaint(color: Int, size: Float, typeface: Typeface? = null) =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        textAlign = Paint.Align.CENTER
        this.typeface = typeface ?: Typeface.DEFAULT
    }
