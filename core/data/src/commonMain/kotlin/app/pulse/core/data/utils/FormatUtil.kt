package app.pulse.core.data.utils

import kotlin.time.Duration

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "${min}:${sec.toString().padStart(2, '0')}"
}

/**
 * m:ss below one hour, h:mm:ss at or above it, matching the YouTube lengthText
 * that online rows carry in Song.durationText.
 */
fun formatDuration(duration: Duration): String =
    duration.toComponents { hours, minutes, seconds, _ ->
        if (hours > 0) {
            "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "$minutes:${seconds.toString().padStart(2, '0')}"
        }
    }
