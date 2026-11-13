package app.pulse.android.utils

import androidx.media3.common.MediaItem
import app.pulse.core.data.utils.songBundle

data class SaveRequest(
    val videoId: String,
    val title: String,
    val artist: String?,
    val durationText: String,
    val likedAt: Long?
) {
    val baseName: String get() = downloadBaseName(title, artist).ifBlank { videoId }

    companion object {
        fun from(mediaItem: MediaItem, likedAt: Long?): SaveRequest = SaveRequest(
            videoId = mediaItem.mediaId,
            title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
            artist = mediaItem.mediaMetadata.artist?.toString(),
            durationText = mediaItem.mediaMetadata.extras?.songBundle?.durationText ?: "",
            likedAt = likedAt
        )
    }
}