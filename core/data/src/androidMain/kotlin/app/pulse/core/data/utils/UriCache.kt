package app.pulse.core.data.utils

import android.net.Uri

class UriCache<Key : Any, Meta>(size: Int = 16) {
    private val buffer = RingBuffer<CachedUri<Key, Meta>?>(size) { null }

    data class CachedUri<Key, Meta> internal constructor(
        val key: Key,
        val meta: Meta,
        val uri: Uri
    )

    operator fun get(key: Key) = buffer.find { it != null && it.key == key }

    fun push(
        key: Key,
        meta: Meta,
        uri: Uri
    ) {
        buffer += CachedUri(key, meta, uri)
    }

    fun remove(key: Key) = buffer.removeIf { it?.key == key }

    fun clear() = buffer.clear()

    // Replace an entry with the same key. //
    fun update(key: Key, meta: Meta, uri: Uri) {
        remove(key)
        push(key, meta, uri)
    }
}

// Whole seconds until the URL's `expire`, negative once past, null when the URL
// states no expiry. A googlevideo signature past its `expire` is rejected by the
// CDN, so this is the one expiry the URL states about itself: the guard and the
// log line both read it here rather than parsing the parameter again.
fun Uri.expiresInSeconds(now: Long = System.currentTimeMillis()): Long? =
    getQueryParameter("expire")?.toLongOrNull()?.let { it - now / 1000 }
