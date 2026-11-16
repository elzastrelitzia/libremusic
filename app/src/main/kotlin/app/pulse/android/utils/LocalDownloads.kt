package app.pulse.android.utils

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import app.pulse.android.Dependencies
import app.pulse.android.Database
import app.pulse.android.service.DOWNLOAD_KEY_PREFIX
import app.pulse.android.service.LOCAL_KEY_PREFIX
import app.pulse.core.data.models.SongEntity
import app.pulse.core.data.utils.songBundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
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

fun mediaStoreId(songId: String): Long? {
    if (!songId.startsWith(LOCAL_KEY_PREFIX)) return null
    if (songId.startsWith(DOWNLOAD_KEY_PREFIX)) return null
    return songId.removePrefix(LOCAL_KEY_PREFIX).toLongOrNull()
}

/** Audio file names in `names` that are not already indexed as `local:download:` rows. */
fun orphanFileNames(names: List<String>, indexed: Set<String>): List<String> =
    names.filter { name -> File(name).extension.lowercase() in audioExtensions && name !in indexed }

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

    /** Insert database rows for saved audio files on disk that have no row yet. */
    fun importOrphans() {
        val dir = dir()
        if (!dir.isDirectory) return
        val names = dir.list()?.toList().orEmpty()
        val indexed = Database.downloadSongIds().mapNotNull { downloadFileName(it) }.toSet()
        orphanFileNames(names, indexed).forEach { name ->
            val file = File(dir, name)
            val baseName = name.substringBeforeLast('.', "")
            val parts = baseName.split(" - ", limit = 2)
            Database.insert(
                SongEntity(
                    id = downloadSongId(name),
                    title = parts.getOrNull(0) ?: baseName,
                    artistsText = parts.getOrNull(1),
                    durationText = null,
                    thumbnailUrl = thumbnailFor(file)?.toUri()?.toString()
                )
            )
        }
    }

    private fun deletePartial(dest: File, request: SaveRequest) {
        dest.listFiles()
            ?.filter { it.name.startsWith("${request.baseName}.") || it.name.startsWith("${request.videoId}.") }
            ?.forEach { it.delete() }
        coversDir().listFiles()
            ?.filter { it.name.startsWith("${request.baseName}.") || it.name.startsWith("${request.videoId}.") }
            ?.forEach { it.delete() }
    }

    suspend fun saveNow(
        context: Context,
        request: SaveRequest,
        progress: SaveProgressListener
    ): SaveResult = withContext(Dispatchers.IO) {
        if (!canWrite(context)) return@withContext SaveResult.MissingPermission

        val dest = ensureDir() ?: return@withContext SaveResult.Failed(null)

        val existing = findExisting(request.videoId, request.baseName)
        if (existing != null) {
            insert(request, existing, thumbnailFor(existing))
            return@withContext SaveResult.AlreadySaved(existing)
        }

        try {
            val json = Dependencies.runSave(
                request.videoId, dest.absolutePath, request.baseName, progress
            )
            if (progress.isCancelled()) {
                deletePartial(dest, request)
                return@withContext SaveResult.Cancelled
            }

            val obj = JSONObject(json)
            val audio = File(obj.getString("path"))
            val thumbnail = obj.optString("thumb").takeIf { it.isNotBlank() }?.let(::File)
            val cover = writeCover(thumbnail, audio.nameWithoutExtension)
            if (cover != null) thumbnail?.delete()
            if (cover != null &&
                (audio.extension.equals("m4a", true) || audio.extension.equals("mp4", true))
            ) {
                runCatching { Dependencies.runEmbedCover(audio.absolutePath, cover.absolutePath) }
            }
            insert(request, audio, cover ?: thumbnailFor(audio))
            SaveResult.Saved(audio)
        } catch (t: Throwable) {
            deletePartial(dest, request)
            if (progress.isCancelled()) SaveResult.Cancelled else SaveResult.Failed(t)
        }
    }

    private fun insert(request: SaveRequest, audio: File, cover: File?) {
        Database.insert(
            SongEntity(
                id = downloadSongId(audio.name),
                title = request.title,
                artistsText = request.artist,
                durationText = request.durationText,
                thumbnailUrl = cover?.toUri()?.toString(),
                likedAt = request.likedAt
            )
        )
    }
}