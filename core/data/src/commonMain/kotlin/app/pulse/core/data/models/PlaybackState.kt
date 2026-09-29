package app.pulse.core.data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class PlaybackState(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val loopMode: LoopMode = LoopMode.NONE,
    val isPlaying: Boolean = false,
    val isEnded: Boolean = false,
    val isLoading: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    // Transient: error is UI state, not persistence state. QueueDatabase stores individual
    // fields, not the whole object, so this never needs to be serialized.
    @Transient
    val error: PlaybackError? = null
)
