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
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import app.pulse.android.R
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

private const val CARD_SCALE = 1.8f
private const val REF_CARD_WIDTH = 384f
private const val REF_CARD_HEIGHT = 512f
private const val REF_CARD_RADIUS = 24f
private const val REF_PADDING = 22f
private const val REF_ART = 340f
private const val REF_ART_RADIUS = 12f
private const val REF_TITLE_SIZE = 32f
private const val REF_TITLE_TOP = 372f
private const val REF_ARTIST_SIZE = 24f
private const val REF_ARTIST_TOP = 411f
private const val REF_FOOTER_SIZE = 24f
private const val REF_FOOTER_TOP = 461f
private const val REF_LOGO_SIZE = 24f
private const val REF_LOGO_GAP = 8f

private const val SHOW_FOOTER_LOGO = false

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
        val card = drawNoLyricsCard(
            art = loadArtwork(context, mediaItem),
            title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
            artist = mediaItem.mediaMetadata.artist?.toString().orEmpty(),
            blurRadius = 32f * density,
            logo = if (SHOW_FOOTER_LOGO) context.getDrawable(R.drawable.app_icon_transparent) else null,
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

private fun drawNoLyricsCard(
    art: Bitmap?,
    title: String,
    artist: String,
    blurRadius: Float,
    logo: Drawable?,
): Bitmap {
    val out = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)

    if (art != null) {
        val cover = RectF(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat())
        val cropped = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(cropped).drawBitmap(art, null, coverRect(art, cover), filterPaint())
        canvas.drawBitmap(blur(cropped, blurRadius), 0f, 0f, null)
        canvas.drawColor(Color.argb(128, 0, 0, 0))
    } else {
        canvas.drawColor(Color.BLACK)
    }

    val cardWidth = REF_CARD_WIDTH * CARD_SCALE
    val padding = REF_PADDING * CARD_SCALE
    val textLeft = (CARD_WIDTH - cardWidth) / 2f + padding
    val textWidth = cardWidth - 2 * padding

    // The title wraps to two lines. The card grows by the wrapped line's height so
    // the artist line and the footer below keep their spacing instead of colliding.
    val titlePaint = textPaint(Color.WHITE, REF_TITLE_SIZE * CARD_SCALE, Typeface.DEFAULT_BOLD)
    val titleLayout = StaticLayout.Builder
        .obtain(title, 0, title.length, titlePaint, textWidth.roundToInt())
        .setIncludePad(false)
        .setMaxLines(2)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()
    val extraForWrappedTitle = if (titleLayout.lineCount > 1) {
        titleLayout.height - (titleLayout.getLineBottom(0) - titleLayout.getLineTop(0))
    } else {
        0
    }

    val cardHeight = REF_CARD_HEIGHT * CARD_SCALE + extraForWrappedTitle
    val left = (CARD_WIDTH - cardWidth) / 2f
    val top = (CARD_HEIGHT - cardHeight) / 2f
    val cardRadius = REF_CARD_RADIUS * CARD_SCALE
    canvas.drawRoundRect(
        RectF(left, top, left + cardWidth, top + cardHeight),
        cardRadius,
        cardRadius,
        fillPaint(Color.argb(128, 0, 0, 0)),
    )

    if (art != null) {
        val size = REF_ART * CARD_SCALE
        val rect = RectF(textLeft, top + padding, textLeft + size, top + padding + size)
        val artRadius = REF_ART_RADIUS * CARD_SCALE
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, artRadius, artRadius, Path.Direction.CW) })
        canvas.drawBitmap(art, null, coverRect(art, rect), filterPaint())
        canvas.restore()
    }

    canvas.save()
    canvas.translate(textLeft, top + REF_TITLE_TOP * CARD_SCALE)
    titleLayout.draw(canvas)
    canvas.restore()

    val artistPaint = textPaint(Color.WHITE, REF_ARTIST_SIZE * CARD_SCALE, mediumTypeface())
    canvas.drawText(
        ellipsize(artist, artistPaint, textWidth),
        textLeft,
        baselineOf(top + REF_ARTIST_TOP * CARD_SCALE + extraForWrappedTitle, artistPaint),
        artistPaint,
    )

    val footerColor = Color.argb(179, 255, 255, 255)
    val footerMedium = textPaint(footerColor, REF_FOOTER_SIZE * CARD_SCALE, mediumTypeface())
    val footerLight = textPaint(
        footerColor,
        REF_FOOTER_SIZE * CARD_SCALE,
        Typeface.create("sans-serif-light", Typeface.NORMAL),
    )
    val footerTop = top + REF_FOOTER_TOP * CARD_SCALE + extraForWrappedTitle
    val footerBaseline = baselineOf(footerTop, footerMedium)
    val footerLeft = if (logo != null) {
        val size = REF_LOGO_SIZE * CARD_SCALE
        val metrics = footerMedium.fontMetrics
        val footerCenter = footerTop + (metrics.descent - metrics.ascent) / 2f
        logo.setBounds(
            textLeft.toInt(),
            (footerCenter - size / 2f).toInt(),
            (textLeft + size).toInt(),
            (footerCenter + size / 2f).toInt(),
        )
        logo.draw(canvas)
        textLeft + size + REF_LOGO_GAP * CARD_SCALE
    } else {
        textLeft
    }

    canvas.drawText("libre", footerLeft, footerBaseline, footerMedium)
    canvas.drawText("music", footerLeft + footerMedium.measureText("libre"), footerBaseline, footerLight)

    return out
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

private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.FILL
}

private fun mediumTypeface() = Typeface.create("sans-serif-medium", Typeface.NORMAL)

private fun baselineOf(boxTop: Float, paint: Paint): Float = boxTop - paint.fontMetrics.ascent

private fun textPaint(color: Int, size: Float, typeface: Typeface? = null) =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        textAlign = Paint.Align.LEFT
        this.typeface = typeface ?: Typeface.DEFAULT
    }
