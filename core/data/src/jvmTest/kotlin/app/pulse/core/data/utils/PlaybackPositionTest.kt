package app.pulse.core.data.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackPositionTest {

    @Test
    fun usesLinePositionWhenAvailable() {
        // 5 s reported by the line, seek base 10 s -> 15000, whatever the byte count says
        assertEquals(15_000L, resolvePosition(5_000_000L, 0L, 192.0, 10_000L))
    }

    @Test
    fun fallsBackToBytesWhenLineReportsNothing() {
        // getMicrosecondPosition() returns 0 on some drivers until the line is running
        assertEquals(30_000L, resolvePosition(0L, 3_840_000L, 192.0, 10_000L))
    }

    @Test
    fun clampsToDuration() {
        // a line that overruns must not report past the end
        assertEquals(60_000L, resolvePosition(999_000_000L, 0L, 192.0, 0L, durationMs = 60_000L))
    }

    @Test
    fun unknownDurationDoesNotClamp() {
        // durationMs = 0 means "not parsed yet", which must not clamp everything to zero
        assertEquals(90_000L, resolvePosition(90_000_000L, 0L, 192.0, 0L, durationMs = 0L))
    }

    @Test
    fun zeroBytesPerMsReturnsSeekBase() {
        // before the WAV header is parsed there is no rate to divide by
        assertEquals(12_000L, resolvePosition(0L, 500_000L, 0.0, 12_000L))
    }
}
