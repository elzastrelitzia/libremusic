package app.pulse.android.utils

import java.io.File

sealed interface SaveResult {
    data class Saved(val audio: File) : SaveResult
    data class AlreadySaved(val audio: File) : SaveResult
    object MissingPermission : SaveResult
    object Cancelled : SaveResult
    data class Failed(val cause: Throwable?) : SaveResult
}