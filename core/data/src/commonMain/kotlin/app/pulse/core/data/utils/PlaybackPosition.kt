package app.pulse.core.data.utils

import kotlin.math.roundToLong

/**
 * Resolve the current playback position in milliseconds.
 *
 * Position has to come from the line, not from bytes written. `write` blocks when the line's
 * internal buffer is full, so the push rate and the consume rate drift apart the moment the
 * buffer is not empty, and the drift is never corrected because nothing ever asks the line
 * where it is.
 *
 * Falls back to the byte count when the line reports nothing, which happens on drivers where
 * `getMicrosecondPosition()` returns 0 until the line is running.
 *
 * @param lineMicros microseconds reported by the line, or 0 when unavailable
 * @param bytesWritten total bytes pushed to the line this pipeline
 * @param bytesPerMs bytes per millisecond at the decoded sample rate
 * @param seekBaseMs position the pipeline started at, for a seek
 * @param durationMs track duration, or 0 when not yet known
 */
fun resolvePosition(
    lineMicros: Long,
    bytesWritten: Long,
    bytesPerMs: Double,
    seekBaseMs: Long,
    durationMs: Long = 0L
): Long {
    val raw = when {
        lineMicros > 0 -> seekBaseMs + lineMicros / 1000
        bytesPerMs > 0 -> seekBaseMs + (bytesWritten / bytesPerMs).roundToLong()
        else -> seekBaseMs
    }
    return if (durationMs > 0) raw.coerceAtMost(durationMs) else raw
}
