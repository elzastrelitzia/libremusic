package app.pulse.android.ui.screens.home

import android.content.Context
import app.pulse.android.preferences.DataPreferences
import coil3.imageLoader
import coil3.request.ImageRequest
import app.pulse.providers.innertube.Innertube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Duration.Companion.days

// Disk-persisted home feed cache (ported from the desktop HomeScreen): JSON
// files in the app's files dir with a user-configurable TTL
// (DataPreferences.homeFeedCacheDays, 0 = disabled). Restored on cold start so
// Quick Picks and Discover render instantly instead of refetching. All disk IO
// runs on Dispatchers.IO.
private val fCacheJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

object HomeCache {
    @Serializable
    data class DiscoverData(val page: Innertube.DiscoverPage? = null)

    @Serializable
    data class RelatedData(val seed: String? = null, val page: Innertube.RelatedPage? = null)

    /** A restored related page plus the seed it was built from, so the caller can
     *  tell whether the cache still matches the song it wants to recommend from. */
    data class CachedRelated(val seed: String?, val page: Innertube.RelatedPage?)

    private val ttlMs: Long
        get() = DataPreferences.homeFeedCacheDays.days.inWholeMilliseconds

    /** Restores a fresh cached discover page, or null when absent/stale/disabled. */
    suspend fun restoreDiscover(filesDir: File): Result<Innertube.DiscoverPage>? =
        restore<DiscoverData, Innertube.DiscoverPage>(filesDir, "discover.json") { it.page }

    /** Restores a fresh cached related page, or null when absent/stale/disabled. */
    suspend fun restoreRelated(filesDir: File): Result<CachedRelated>? =
        restore<RelatedData, CachedRelated>(filesDir, "related.json") { CachedRelated(it.seed, it.page) }

    suspend fun saveDiscover(filesDir: File, page: Innertube.DiscoverPage) =
        save(filesDir, "discover.json", DiscoverData(page))

    suspend fun saveRelated(filesDir: File, seed: String?, page: Innertube.RelatedPage) =
        save(filesDir, "related.json", RelatedData(seed, page))

    /**
     * Warms Coil's disk cache with every home-feed thumbnail so the cached feed
     * renders fully offline. Idempotent: already-cached URLs resolve from disk,
     * zero network. LRU eviction of home thumbs becomes a non-issue within the
     * cache TTL.
     */
    fun prefetchThumbs(
        context: Context,
        discover: Innertube.DiscoverPage?,
        related: Innertube.RelatedPage?
    ) {
        val urls = listOfNotNull(
            discover?.newReleaseAlbums?.mapNotNull { it.thumbnail?.url },
            discover?.trending?.songs?.mapNotNull { it.thumbnail?.url },
            related?.songs?.mapNotNull { it.thumbnail?.url }
        ).flatten().distinct()

        urls.forEach { url ->
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .memoryCacheKey(url)
                    .build()
            )
        }
    }

    // Stale or corrupt cache is deleted so the next open fetches fresh data.
    private suspend inline fun <reified T, reified R> restore(
        filesDir: File,
        name: String,
        crossinline extract: (T) -> R?
    ): Result<R>? = withContext(Dispatchers.IO) {
        if (ttlMs <= 0L) return@withContext null
        val file = File(filesDir, "home/$name")
        try {
            if (file.exists() && System.currentTimeMillis() - file.lastModified() <= ttlMs) {
                val value = extract(fCacheJson.decodeFromString<T>(file.readText()))
                if (value != null) Result.success(value) else {
                    file.delete()
                    null
                }
            } else {
                if (file.exists()) file.delete()
                null
            }
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    private suspend inline fun <reified T> save(
        filesDir: File,
        name: String,
        data: T
    ) {
        if (ttlMs <= 0L) return
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(filesDir, "home/$name")
                file.parentFile?.mkdirs()
                file.writeText(fCacheJson.encodeToString(data))
            }
        }
    }
}
