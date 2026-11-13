package app.pulse.android.utils

interface SaveProgressListener {
    fun onProgress(downloaded: Long, total: Long)
    fun isCancelled(): Boolean
}