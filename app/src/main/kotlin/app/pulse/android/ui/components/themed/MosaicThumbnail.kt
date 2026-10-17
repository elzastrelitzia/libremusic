package app.pulse.android.ui.components.themed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import app.pulse.android.utils.thumbnail
import app.pulse.core.ui.LocalAppearance
import coil3.compose.AsyncImage

@Composable
fun MosaicThumbnail(
    urls: List<String>,
    modifier: Modifier = Modifier
) {
    val colorPalette = LocalAppearance.current.colorPalette
    val placeholder = remember { ColorPainter(colorPalette.background1) }

    @Composable
    fun cell(url: String, size: Int, cellModifier: Modifier) {
        AsyncImage(
            model = url.thumbnail(size) ?: url,
            contentDescription = null,
            placeholder = placeholder,
            contentScale = ContentScale.Crop,
            modifier = cellModifier
        )
    }

    if (urls.size == 1) {
        cell(urls.first(), 512, modifier)
    } else {
        Box(modifier = modifier) {
            listOf(
                Alignment.TopStart,
                Alignment.TopEnd,
                Alignment.BottomStart,
                Alignment.BottomEnd
            ).forEachIndexed { index, alignment ->
                urls.getOrNull(index)?.let { url ->
                    cell(
                        url,
                        256,
                        Modifier
                            .align(alignment)
                            .fillMaxSize(0.5f)
                    )
                }
            }
        }
    }
}
