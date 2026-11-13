package app.pulse.android.utils

fun savePercent(downloaded: Long, total: Long): Int? =
    if (total <= 0L) null else ((downloaded * 100) / total).coerceIn(0L, 100L).toInt()

class SaveProgressThrottle(private val intervalMs: Long = 500L) {
    private var lastPercent = -1
    private var lastAt: Long? = null

    fun shouldEmit(percent: Int, now: Long): Boolean {
        if (percent == lastPercent) return false
        val previous = lastAt
        if (percent < 100 && previous != null && now - previous < intervalMs) return false
        lastPercent = percent
        lastAt = now
        return true
    }
}