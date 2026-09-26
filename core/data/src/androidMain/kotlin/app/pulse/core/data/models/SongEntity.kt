package app.pulse.core.data.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Song")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistsText: String? = null,
    val durationText: String?,
    val thumbnailUrl: String?,
    val likedAt: Long? = null,
    val totalPlayTimeMs: Long = 0,
    val loudnessBoost: Float? = null,
    @ColumnInfo(defaultValue = "false")
    val blacklisted: Boolean = false,
    @ColumnInfo(defaultValue = "false")
    val explicit: Boolean = false,
    @ColumnInfo(defaultValue = "false")
    val missing: Boolean = false
) {
    fun toggleLike() = copy(likedAt = if (likedAt == null) System.currentTimeMillis() else null)
}

fun SongEntity.toSong() = Song(
    id = id,
    title = title,
    artistsText = artistsText,
    durationText = durationText,
    thumbnailUrl = thumbnailUrl,
    likedAt = likedAt,
    totalPlayTimeMs = totalPlayTimeMs,
    blacklisted = blacklisted,
    explicit = explicit
)

fun Song.toEntity() = SongEntity(
    id = id,
    title = title,
    artistsText = artistsText,
    durationText = durationText,
    thumbnailUrl = thumbnailUrl,
    likedAt = likedAt,
    totalPlayTimeMs = totalPlayTimeMs,
    loudnessBoost = null, // Needs to be handled if used
    blacklisted = blacklisted,
    explicit = explicit
)
