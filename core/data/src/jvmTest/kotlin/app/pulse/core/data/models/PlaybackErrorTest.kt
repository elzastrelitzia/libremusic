package app.pulse.core.data.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlaybackErrorTest {

    @Test
    fun mapsEachErrorToItsOwnMessage() {
        val errors = listOf(
            PlaybackError.NoStreamUrl(""),
            PlaybackError.SourceRemoved(""),
            PlaybackError.LoginRequired(""),
            PlaybackError.RegionBlocked(""),
            PlaybackError.NoAudioDevice(""),
            PlaybackError.FormatUnsupported(""),
            PlaybackError.Unknown("")
        )
        val messages = errors.map { it.describe("fallback") }
        assertEquals(errors.size, messages.toSet().size, "each error type must map to a distinct message")
        messages.forEach { assertTrue(it.isNotBlank(), "message must not be blank") }
    }

    @Test
    fun nullFallsBackToCallerText() {
        val error: PlaybackError? = null
        assertEquals("something went wrong", error?.describe("something went wrong") ?: "something went wrong")
    }

    @Test
    fun blankCauseMessageStillDescribes() {
        // cause.message == "" must not produce an empty string
        val error = PlaybackError.Unknown("")
        assertTrue(error.describe("fallback").isNotBlank())
    }

    @Test
    fun nonBlankCauseMessageIsUsed() {
        val error = PlaybackError.NoStreamUrl("yt-dlp returned no URL")
        assertEquals("yt-dlp returned no URL", error.describe("fallback"))
    }
}
