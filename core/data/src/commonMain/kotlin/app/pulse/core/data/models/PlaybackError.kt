package app.pulse.core.data.models

/**
 * Named playback failures.
 *
 * The engine used to flatten every error to a single string via `cause.message ?: simpleName`,
 * which meant four different root causes produced four different opaque strings and the UI could
 * not tell them apart. Each case here maps to a distinct user-facing message.
 */
sealed class PlaybackError(val message: String) {
    class NoStreamUrl(message: String) : PlaybackError(message)
    class SourceRemoved(message: String) : PlaybackError(message)
    class LoginRequired(message: String) : PlaybackError(message)
    class RegionBlocked(message: String) : PlaybackError(message)
    class NoAudioDevice(message: String) : PlaybackError(message)
    class FormatUnsupported(message: String) : PlaybackError(message)
    class Unknown(message: String) : PlaybackError(message)

    /**
     * A blank cause message must not produce an empty string, so fall back to the type name.
     * A null error uses the caller's own wording.
     */
    fun describe(fallback: String): String =
        message.takeIf { it.isNotBlank() } ?: this::class.simpleName ?: fallback
}
