package app.pulse.android.utils

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.widget.Toast
import app.pulse.android.Dependencies
import app.pulse.android.R
import app.pulse.android.Database
import app.pulse.android.service.DOWNLOAD_KEY_PREFIX
import app.pulse.core.data.models.SongEntity
import app.pulse.core.data.utils.songBundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

private val audioExtensions = setOf("m4a", "webm", "opus", "mp4", "aac", "ogg", "mp3")
private val imageExtensions = setOf("webp", "jpg", "jpeg", "png")

fun downloadSongId(fileName: String): String = DOWNLOAD_KEY_PREFIX + fileName

fun downloadFileName(songId: String): String? {
    if (!songId.startsWith(DOWNLOAD_KEY_PREFIX)) return null
    val name = songId.removePrefix(DOWNLOAD_KEY_PREFIX)
    return name.takeIf { it.isNotBlank() && File(it).name == it }
}

fun pickFileName(names: List<String>, baseName: String, extensions: Set<String>): String? =
    names.firstOrNull { name ->
        val ext = name.substringAfterLast('.', "").lowercase()
        ext in extensions && name.substringBeforeLast('.', "") == baseName
    }

private val invalidNameChars = Regex("""[\\/:*?"<>|\x00-\x1F]""")
private val whitespace = Regex("\\s+")

/**
 * Filesystem-safe song name: `Title - Artist`, capped at 60 chars.
 * 60 chars of 4-byte UTF-8 is 240 bytes, under the 255-byte file name limit
 * even after the audio, thumbnail, and `.part` suffixes.
 * Returns empty when there is no usable title.
 */
fun downloadBaseName(title: String?, artist: String?): String =
    listOfNotNull(title?.takeIf { it.isNotBlank() }, artist?.takeIf { it.isNotBlank() })
        .joinToString(" - ")
        .replace(invalidNameChars, " ")
        .replace(whitespace, " ")
        .trim()
        .trim('.')
        .take(60)
        .trim()
        .trim('.')

object LocalDownloads {
    private const val DOWNLOAD_DIR_NAME = "libremusic"

    fun dir(): File = File(Environment.getExternalStorageDirectory(), DOWNLOAD_DIR_NAME)

    fun canWrite(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

    private fun coversDir(): File = File(dir(), ".covers").apply { mkdirs() }

    private fun ensureDir(): File? = dir().let { directory ->
        if (!directory.isDirectory && !directory.mkdirs()) return null
        // Keep MediaStore from indexing these files: they are listed from the
        // database as `local:download:` ids, a second MediaStore row would
        // show every saved song twice.
        File(directory, ".nomedia").takeIf { !it.exists() }?.runCatching { createNewFile() }
        directory
    }

    fun fileFor(songId: String): File? =
        downloadFileName(songId)?.let { File(dir(), it) }

    fun findExisting(videoId: String, baseName: String): File? {
        val names = dir().list()?.toList().orEmpty()
        val match = pickFileName(names, baseName, audioExtensions)
            ?: pickFileName(names, videoId, audioExtensions)
        return match?.let { File(dir(), it) }
    }

    fun thumbnailFor(audioFile: File): File? {
        val hidden = File(coversDir(), "${audioFile.nameWithoutExtension}.jpg")
        if (hidden.isFile) return hidden
        return pickFileName(
            dir().list()?.toList().orEmpty(),
            audioFile.nameWithoutExtension,
            imageExtensions
        )?.let { File(dir(), it) }
    }

    private fun writeCover(thumbnail: File?, baseName: String): File? {
        if (thumbnail == null || !thumbnail.isFile) return null
        val bitmap = BitmapFactory.decodeFile(thumbnail.absolutePath) ?: return null
        val cover = File(coversDir(), "$baseName.jpg")
        return runCatching {
            FileOutputStream(cover).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            cover
        }.getOrNull()
    }

    fun delete(songId: String) {
        val audio = fileFor(songId) ?: return
        thumbnailFor(audio)?.delete()
        audio.delete()
    }

    /** Remove download rows whose audio file no longer exists on disk. */
    fun pruneMissing() {
        val stale = Database.downloadSongIds().filter { id -> fileFor(id)?.isFile != true }
        stale.forEach { id ->
            delete(id)
            Database.deleteSongById(id)
        }
    }

    // ponytail: app-process download. Move to WorkManager or a foreground service
    // if downloads must survive process death.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val inFlight = mutableSetOf<String>()

    /** Returns true when no download is running for [key]. */
    internal fun beginDownload(key: String): Boolean =
        synchronized(inFlight) { inFlight.add(key) }

    internal fun endDownload(key: String) {
        synchronized(inFlight) { inFlight.remove(key) }
    }

    fun save(context: Context, mediaItem: MediaItem, likedAt: Long?) {
        val appContext = context.applicationContext
        val videoId = mediaItem.mediaId
        val baseName = downloadBaseName(
            mediaItem.mediaMetadata.title?.toString(),
            mediaItem.mediaMetadata.artist?.toString()
        ).ifBlank { videoId }

        if (!beginDownload(baseName)) {
            Toast.makeText(
                appContext,
                R.string.already_downloading,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        scope.launch {
            try {
                if (!canWrite(appContext)) {
                    toast(appContext, R.string.grant_file_access_to_save)
                    return@launch
                }

                val dest = ensureDir()
                if (dest == null) {
                    toast(appContext, R.string.save_to_local_failed)
                    return@launch
                }

                val existing = findExisting(videoId, baseName)
                if (existing != null) {
                    insert(mediaItem, existing, thumbnailFor(existing), likedAt)
                    toast(appContext, R.string.already_saved_to_local)
                    return@launch
                }

                toast(appContext, R.string.saving_to_local)
                try {
                    val json = Dependencies.runSave(videoId, dest.absolutePath, baseName)
                    val obj = JSONObject(json)
                    val audio = File(obj.getString("path"))
                    val thumbnail = obj.optString("thumb").takeIf { it.isNotBlank() }?.let(::File)
                    val cover = writeCover(thumbnail, audio.nameWithoutExtension)
                    if (cover != null) thumbnail?.delete()
                    if (cover != null && (audio.extension.equals("m4a", true) || audio.extension.equals("mp4", true))) {
                        runCatching { Dependencies.runEmbedCover(audio.absolutePath, cover.absolutePath) }
                    }
                    insert(mediaItem, audio, cover ?: thumbnailFor(audio), likedAt)
                    toast(appContext, R.string.saved_to_local)
                } catch (t: Throwable) {
                    dest.listFiles()
                        ?.filter { it.name.startsWith("$baseName.") || it.name.startsWith("$videoId.") }
                        ?.forEach { it.delete() }
                    coversDir().listFiles()
                        ?.filter { it.name.startsWith("$baseName.") || it.name.startsWith("$videoId.") }
                        ?.forEach { it.delete() }
                    toast(appContext, R.string.save_to_local_failed)
                }
            } finally {
                endDownload(baseName)
            }
        }
    }

    private fun insert(mediaItem: MediaItem, audio: File, cover: File?, likedAt: Long?) {
        val extras = mediaItem.mediaMetadata.extras?.songBundle
        Database.insert(
            SongEntity(
                id = downloadSongId(audio.name),
                title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
                artistsText = mediaItem.mediaMetadata.artist?.toString(),
                durationText = extras?.durationText ?: "",
                thumbnailUrl = cover?.toUri()?.toString(),
                likedAt = likedAt
            )
        )
    }

    private suspend fun toast(context: Context, text: Int) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
        }
    }
}
