package app.pulse.android.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    indices = [
        Index(
            value = ["query"],
            unique = true
        )
    ]
)
data class SearchQuery(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    // Set when the entry was saved by playing a search preview
    val songId: String? = null,
    val thumbnailUrl: String? = null,
    val artistsText: String? = null
)
