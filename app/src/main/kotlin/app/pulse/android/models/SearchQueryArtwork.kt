package app.pulse.android.models

import androidx.compose.runtime.Immutable

@Immutable
data class SearchQueryArtwork(
    val id: Long,
    val query: String,
    val thumbnailUrl: String?,
    val artistsText: String?
)
