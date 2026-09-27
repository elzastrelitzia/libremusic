package app.pulse.core.data.repository

import app.pulse.core.data.models.Song
import app.pulse.core.data.models.PlaybackState
import app.pulse.core.data.models.LoopMode
import app.pulse.core.data.utils.AppDirs
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.sql.Connection
import java.sql.DriverManager

/**
 * Persists and restores the playback queue + position using local SQLite.
 * Mirrors Android's maybeSavePlayerQueue / maybeRestorePlayerQueue pattern.
 */
object QueueDatabase {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        classDiscriminator = "#class"
    }

    private var connection: Connection? = null

    /** Schema version for future migrations. */
    private const val SCHEMA_VERSION = 1

    /** Save data: queue items, current index, playback position, loop mode, volume. */
    data class SavedQueue(
        val queue: List<SongEntry>,
        val currentIndex: Int,
        val positionMs: Long,
        val durationMs: Long,
        val loopMode: LoopMode,
        val volume: Float
    )

    data class SongEntry(
        val videoId: String,
        val songJson: String   // JSON-serialized app.pulse.core.data.models.Song
    )

    /** Ensure DB + table exist. Called once at app start. */
    fun init() {
        Class.forName("org.sqlite.JDBC")
        val dbFile = AppDirs.queueDb
        val conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        connection = conn

        conn.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS player_queue (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    video_id    TEXT    NOT NULL UNIQUE,
                    song_json   TEXT    NOT NULL,
                    queue_index INTEGER NOT NULL,
                    is_current  INTEGER NOT NULL DEFAULT 0,
                    position_ms INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS player_meta (
                    key   TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """.trimIndent()
            )
        }

        // Ensure schema version is set
        connection?.createStatement()?.executeQuery("SELECT value FROM player_meta WHERE key = 'schema_version'").use { rs ->
            if (rs != null && !rs.next()) {
                connection?.createStatement()?.use { stmt ->
                    stmt.execute("INSERT INTO player_meta (key, value) VALUES ('schema_version', '$SCHEMA_VERSION')")
                }
            }
        }
    }

    /** Save current queue state. Thread-safe. */
    fun save(state: PlaybackState): Unit = synchronized(this) {
        val conn = connection ?: return@synchronized

        // clear old data
        conn.createStatement().use { stmt ->
            stmt.execute("DELETE FROM player_queue")
            stmt.execute("DELETE FROM player_meta WHERE key != 'schema_version'")
        }

        val queue = state.queue
        if (queue.isEmpty()) return@synchronized

        conn.prepareStatement(
            "INSERT OR IGNORE INTO player_queue (video_id, song_json, queue_index, is_current, position_ms) VALUES (?, ?, ?, ?, ?)"
        ).use { stmt ->
            val currentVideoId = queue.getOrNull(state.currentIndex)?.id

            queue.forEachIndexed { index, song ->
                val videoId = song.id
                val songStr = json.encodeToString(song)
                val isCurrent = if (videoId == currentVideoId) 1 else 0
                val pos = if (isCurrent == 1) state.currentPositionMs else 0L

                stmt.setString(1, videoId)
                stmt.setString(2, songStr)
                stmt.setInt(3, index)
                stmt.setInt(4, isCurrent)
                stmt.setLong(5, pos)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }

        // save meta: currentIndex, loopMode, volume
        conn.prepareStatement("INSERT OR REPLACE INTO player_meta (key, value) VALUES (?, ?)").use { metaStmt ->
            metaStmt.setString(1, "current_index")
            metaStmt.setString(2, state.currentIndex.toString())
            metaStmt.addBatch()

            metaStmt.setString(1, "loop_mode")
            metaStmt.setString(2, state.loopMode.name)
            metaStmt.addBatch()

            metaStmt.setString(1, "volume")
            metaStmt.setString(2, state.volume.toString())
            metaStmt.addBatch()

            metaStmt.setString(1, "duration_ms")
            metaStmt.setString(2, state.durationMs.toString())
            metaStmt.addBatch()

            metaStmt.executeBatch()
        }
    }

    /** Restore saved queue state. Thread-safe. Returns null if nothing saved. */
    fun restore(): SavedQueue? = synchronized(this) {
        val conn = connection ?: return null

        val entries = mutableListOf<SongEntry>()
        var currentPositionMs = 0L

        conn.createStatement().executeQuery(
            "SELECT video_id, song_json, queue_index, is_current, position_ms FROM player_queue ORDER BY queue_index ASC"
        ).use { queueRs ->
            while (queueRs.next()) {
                val videoId = queueRs.getString("video_id")
                val songJson = queueRs.getString("song_json")
                val pos = queueRs.getLong("position_ms")
                if (queueRs.getInt("is_current") == 1) {
                    currentPositionMs = pos
                }
                entries.add(SongEntry(videoId = videoId, songJson = songJson))
            }
        }

        if (entries.isEmpty()) return null

        // restore meta
        val metaMap = mutableMapOf<String, String>()
        conn.createStatement().executeQuery("SELECT key, value FROM player_meta").use { metaRs ->
            while (metaRs.next()) {
                metaMap[metaRs.getString("key")] = metaRs.getString("value")
            }
        }

        val currentIndex = metaMap["current_index"]?.toIntOrNull() ?: 0
        val loopMode = metaMap["loop_mode"]?.let { runCatching { LoopMode.valueOf(it) }.getOrNull() } ?: LoopMode.NONE
        val volume = metaMap["volume"]?.toFloatOrNull() ?: 1f
        val durationMs = metaMap["duration_ms"]?.toLongOrNull() ?: 0L

        return SavedQueue(
            queue = entries,
            currentIndex = currentIndex.coerceIn(0, entries.lastIndex),
            positionMs = currentPositionMs,
            durationMs = durationMs,
            loopMode = loopMode,
            volume = volume
        )
    }

    /** clear saved queue (e.g. after successful restore). Thread-safe. */
    fun clear() {
        synchronized(this) {
            val conn = connection ?: return@synchronized
            conn.createStatement().use { stmt ->
                stmt.execute("DELETE FROM player_queue")
                stmt.execute("DELETE FROM player_meta WHERE key != 'schema_version'")
            }
        }
    }

    /** Close DB connection. */
    fun close() {
        synchronized(this) {
            connection?.close()
            connection = null
        }
    }
}
