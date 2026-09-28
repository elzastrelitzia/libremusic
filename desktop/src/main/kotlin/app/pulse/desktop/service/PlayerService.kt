package app.pulse.desktop.service

import app.pulse.core.data.models.Song
import app.pulse.core.data.models.LoopMode
import app.pulse.core.data.repository.QueueDatabase
import app.pulse.core.data.utils.AppDirs
import app.pulse.core.data.utils.NativeBinaries
import app.pulse.core.data.utils.toSong
import app.pulse.desktop.ui.utils.log as sharedLog
import app.pulse.providers.innertube.Innertube
import app.pulse.providers.innertube.models.PlayerResponse
import app.pulse.providers.innertube.models.bodies.NextBody
import app.pulse.providers.innertube.models.bodies.PlayerBody
import app.pulse.providers.innertube.requests.nextPage
import app.pulse.providers.innertube.requests.player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10
import kotlin.math.roundToLong

// bridge: components.log(tag, msg) → private log(msg) so all 30+ call sites unchanged
private fun log(msg: String) {
    sharedLog("PlayerService", msg)
}

/** yt-dlp hands out media URLs that intermittently 403. A fresh run gets a fresh URL. */
private const val DOWNLOAD_ATTEMPTS = 3

/** return value from playViaStream to inform the caller what action to take. */
private enum class StreamEnd {
    /** stream ended naturally at expected position → safe to advance to next. */
    COMPLETED,
    /** stream did not complete (cancelled/paused) → caller does nothing. */
    INTERRUPTED,
    /** cache-hit stream ended naturally but position << duration → cache was incomplete. */
    INCOMPLETE_CACHE
}

class PlayerService {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var playbackJob: Job? = null
    private var line: SourceDataLine? = null
    private var stream: AudioInputStream? = null
    private var decodeProcess: Process? = null
    private var ytDlpProcess: Process? = null

    /** Bytes yt-dlp fed the tee. Zero means the download failed, not that the track is short. */
    private val fedBytes = AtomicLong(0L)

    @Volatile
    private var isPaused = false
    private var bytesPerMs = 0.0
    @Volatile
    private var seekBaseMs = 0L
    private var currentVideoId: String? = null
    private var currentPlayerResponse: PlayerResponse? = null
    @Volatile
    private var lastSeekMs = 0L
    @Volatile
    private var currentPipelineGen = 0L

    private var radioJob: Job? = null

    companion object {
        private val cacheDir = AppDirs.audio

        /** max cache size in bytes (500 MB). */
        private const val MAX_CACHE_BYTES = 500L * 1024 * 1024

        /** max age in milliseconds (24 hours). */
        private const val MAX_CACHE_AGE_MS = 24L * 60 * 60 * 1000

        /** run cache cleanup on JVM load. */
        fun cleanCache() {
            val dir = cacheDir
            if (!dir.isDirectory) return

            val now = System.currentTimeMillis()
            val files = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".done") }?.toMutableList()
                ?: return

            val expired = mutableListOf<File>()
            val kept = mutableListOf<File>()
            for (f in files) {
                if (now - f.lastModified() > MAX_CACHE_AGE_MS) expired.add(f)
                else kept.add(f)
            }
            for (f in expired) {
                File(dir, "${f.name}.done").delete()
                f.delete()
            }
            log("cache: deleted ${expired.size} expired files")

            var totalBytes = kept.sumOf { it.length() }
            if (totalBytes <= MAX_CACHE_BYTES) return

            kept.sortBy { it.lastModified() }
            val toDelete = mutableListOf<File>()
            for (f in kept) {
                if (totalBytes <= MAX_CACHE_BYTES) break
                toDelete.add(f)
                totalBytes -= f.length()
            }
            for (f in toDelete) {
                File(dir, "${f.name}.done").delete()
                f.delete()
            }
            log("cache: deleted ${toDelete.size} oldest files to stay under ${MAX_CACHE_BYTES / (1024*1024)} MB")
        }
    }

    init {
        cleanCache()
        QueueDatabase.init()
        maybeRestoreQueue()
    }

    // -- Queue management (mirrors Android pattern) ----------------------------

    fun play(song: Song) {
        playFromQueue(listOf(song), index = 0)
    }

    fun playFromQueue(queue: List<Song>, index: Int) {
        if (queue.isEmpty()) return
        val idx = index.coerceIn(0, queue.lastIndex)
        _state.update { it.copy(queue = queue, currentIndex = idx) }
        maybeSaveQueue()
        playInternal(queue[idx])
    }

    fun playNext() {
        val s = _state.value
        val nextIdx = when (s.loopMode) {
            LoopMode.ONE -> s.currentIndex
            else -> {
                val n = s.currentIndex + 1
                if (n >= s.queue.size) 0 else n
            }
        }
        _state.update { it.copy(currentIndex = nextIdx) }
        maybeSaveQueue()
        playInternal(s.queue[nextIdx])
    }

    fun playPrevious() {
        val s = _state.value
        // seek to start if past threshold, else go to previous track
        if (s.currentPositionMs > 3000L && s.currentSong != null) {
            seek(0L)
            return
        }
        val prevIdx = when (s.loopMode) {
            LoopMode.ONE -> s.currentIndex
            LoopMode.ALL -> {
                val p = s.currentIndex - 1
                if (p < 0) s.queue.lastIndex else p
            }
            LoopMode.NONE -> (s.currentIndex - 1).coerceAtLeast(0)
        }
        _state.update { it.copy(currentIndex = prevIdx) }
        maybeSaveQueue()
        playInternal(s.queue[prevIdx])
    }

    fun enqueue(song: Song) {
        _state.update { it.copy(queue = it.queue + song) }
        maybeSaveQueue()
    }

    fun addNext(song: Song) {
        _state.update { s ->
            val idx = (s.currentIndex + 1).coerceIn(0, s.queue.size)
            val q = s.queue.toMutableList().apply { add(idx, song) }
            s.copy(queue = q)
        }
        maybeSaveQueue()
    }

    fun removeFromQueue(index: Int) {
        _state.update { s ->
            if (index < 0 || index >= s.queue.size) return@update s
            val q = s.queue.toMutableList().apply { removeAt(index) }
            if (q.isEmpty()) {
                // last song removed clear everything
                return@update s.copy(
                    queue = emptyList(),
                    currentIndex = -1,
                    currentSong = null,
                    isPlaying = false,
                    isEnded = true
                )
            }
            // adjust currentIndex when removing before current position
            val newIdx = when {
                index < s.currentIndex -> s.currentIndex - 1
                index == s.currentIndex -> 0.coerceAtMost(q.lastIndex)
                else -> s.currentIndex
            }
            s.copy(queue = q, currentIndex = newIdx, currentSong = q.getOrNull(newIdx))
        }
        maybeSaveQueue()
    }

    fun moveInQueue(from: Int, to: Int) {
        _state.update { s ->
            if (from < 0 || from >= s.queue.size || to < 0 || to >= s.queue.size || from == to)
                return@update s
            val q = s.queue.toMutableList()
            val item = q.removeAt(from)
            q.add(to, item)
            // adjust currentIndex when moving items
            val adjIdx = when {
                s.currentIndex == from -> to
                from < s.currentIndex && to >= s.currentIndex -> s.currentIndex - 1
                from > s.currentIndex && to <= s.currentIndex -> s.currentIndex + 1
                else -> s.currentIndex
            }
            s.copy(queue = q, currentIndex = adjIdx)
        }
        maybeSaveQueue()
    }

    fun shuffleQueue() {
        _state.update { s ->
            if (s.queue.size <= 1) return@update s
            val current = s.queue[s.currentIndex]
            val rest = s.queue.toMutableList().apply { removeAt(s.currentIndex) }.shuffled()
            s.copy(queue = listOf(current) + rest, currentIndex = 0)
        }
        maybeSaveQueue()
        log("shuffle: queue shuffled, ${_state.value.queue.size} items")
    }

    fun setupRadio(videoId: String) {
        radioJob?.cancel()
        maybeProcessRadio(videoId)
    }

    fun stopRadio() {
        radioJob?.cancel()
        radioJob = null
    }

    private fun maybeProcessRadio(videoId: String? = null) {
        val s = _state.value
        val remaining = s.queue.size - s.currentIndex - 1
        if (remaining > 2) return

        radioJob?.cancel()
        radioJob = scope.launch {
            val vid = videoId ?: currentVideoId ?: return@launch
            log("radio: fetching related songs for $vid")
            val response = Innertube.nextPage(NextBody(videoId = vid))
            val page = response?.getOrNull() ?: return@launch
            val songs = page.itemsPage?.items?.map { it.toSong() } ?: return@launch
            if (songs.isEmpty()) return@launch
            _state.update { s ->
                val existingIds = s.queue.mapNotNull { it.id }.toSet()
                val newSongs = songs.filter { it.id !in existingIds }
                s.copy(queue = s.queue + newSongs)
            }
            val added = _state.value.queue.size - s.queue.size
            log("radio: added $added new songs to queue (${songs.size} fetched, ${songs.size - added} dupes)")
        }
    }

    fun setLoopMode(mode: LoopMode) {
        _state.update { it.copy(loopMode = mode) }
    }

    fun cycleLoopMode() {
        _state.update { s ->
            val next = when (s.loopMode) {
                LoopMode.NONE -> LoopMode.ONE
                LoopMode.ONE -> LoopMode.ALL
                LoopMode.ALL -> LoopMode.NONE
            }
            s.copy(loopMode = next)
        }
    }


    /**
     * Report a playback failure so the UI can show it. e.message is often null or blank,
     * which would store an error the listener never sees, so fall back to the type name.
     */
    private fun fail(cause: Throwable) {
        val message = cause.message?.takeIf { it.isNotBlank() } ?: cause.javaClass.simpleName
        _state.update { it.copy(isLoading = false, error = message) }
    }

    private fun playInternal(song: Song, startMs: Long = 0L) {
        val videoId = song.id

        // debounce: skip if already loading/playing same video
        val s = _state.value
        if (s.isLoading || s.isPlaying) {
            if (s.currentSong?.id == videoId) {
                log("playInternal: debounced (already on $videoId)")
                return
            }
        }

        playbackJob?.cancel()
        stopAudio()
        currentPipelineGen++

        _state.update {
            PlaybackState(
                queue = it.queue,
                currentIndex = it.currentIndex,
                loopMode = it.loopMode,
                currentSong = song,
                volume = it.volume,
                isLoading = true,
                currentPositionMs = it.currentPositionMs,
                durationMs = it.durationMs
            )
        }

        currentVideoId = videoId
        log("play: ${song.title} (id=$currentVideoId)")

        maybeProcessRadio(videoId)

        playbackJob = scope.launch {
            try {
                val response = Innertube.player(PlayerBody(videoId = currentVideoId!!))
                currentPlayerResponse = response?.getOrNull()
                startPipeline(currentVideoId!!, currentPlayerResponse, startMs = startMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("play error: ${e.message}")
                fail(e)
            }
        }
    }

    fun pause() {
        if (!_state.value.isPlaying) return
        isPaused = true
        runCatching { line?.stop() }
        _state.update { it.copy(isPlaying = false) }
        maybeSaveQueue()
        log("pause")
    }

    fun resume() {
        val s = _state.value
        if (s.isPlaying) return
        val song = s.currentSong ?: return

        if (s.isEnded) {
            log("resume from ended → restart")
            playInternal(song)
            return
        }

        if (!isPaused) {
            // Restored from DB line never started, start pipeline without wiping queue
            if (line == null) {
                playInternal(song, startMs = s.currentPositionMs)
                return
            }
            play(song)
            return
        }

        isPaused = false
        runCatching { line?.start() }
        _state.update { it.copy(isPlaying = true) }
        log("resume")
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        stopAudio()
        maybeSaveQueue()
        _state.update {
            PlaybackState(
                queue = it.queue,
                loopMode = it.loopMode,
                volume = it.volume
            )
        }
        log("stop")
    }

    fun seek(positionMs: Long) {
        val vid = currentVideoId ?: return
        val resp = currentPlayerResponse
        val dur = _state.value.durationMs

        _state.update { it.copy(currentPositionMs = positionMs) }

        if (dur > 0 && positionMs >= dur) {
            log("seek $positionMs ≥ $dur → endSong")
            advanceOrStop()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastSeekMs < 500L) {
            log("seek $positionMs debounced")
            return
        }
        lastSeekMs = now

        log("seek $positionMs")
        startPipeline(vid, resp, startMs = positionMs)
    }

    fun skipForward(sec: Int = 10) {
        val cur = _state.value.currentPositionMs
        val dur = _state.value.durationMs
        val maxSeek = (dur - 1000L).coerceAtLeast(0L)
        val target = (cur + sec * 1000L).coerceIn(0L, maxSeek)
        log("skipForward $sec → $target")
        seek(target)
    }

    fun skipBackward(sec: Int = 10) {
        val cur = _state.value.currentPositionMs
        val target = (cur - sec * 1000L).coerceAtLeast(0L)
        log("skipBackward $sec → $target")
        seek(target)
    }

    fun setVolume(volume: Float) {
        _state.update { it.copy(volume = volume.coerceIn(0f, 1f)) }
        applyVolume()
    }

    private fun applyVolume() {
        val audioLine = line ?: return
        if (!audioLine.isOpen) return
        try {
            val gain = audioLine.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl ?: return
            val vol = _state.value.volume
            val minDb = gain.minimum
            val maxDb = gain.maximum
            val db = if (vol <= 0f) minDb
            else (20f * log10(vol)).coerceIn(minDb, maxDb)
            gain.value = db
        } catch (_: IllegalArgumentException) { }
    }
    private fun startPipeline(
        videoId: String,
        playerResponse: PlayerResponse?,
        startMs: Long,
        // yt-dlp gets an intermittent "HTTP Error 403: Forbidden" on the media URL and
        // writes nothing to stdout. A fresh run gets a fresh URL, so retry a couple of
        // times before telling the user anything.
        downloadAttempt: Int = 0
    ) {
        playbackJob?.cancel()
        stopAudio()

        seekBaseMs = startMs
        val myGen = ++currentPipelineGen

        val dur = playerResponse?.streamingData?.highestQualityFormat?.approxDurationMs
        if (dur != null && startMs >= dur) {
            _state.update { it.copy(isLoading = false, currentPositionMs = dur, isPlaying = false) }
            advanceOrStop()
            return
        }

        val pipelineId = System.identityHashCode(this).toString() + "-" + System.nanoTime()
        log("pipeline[$pipelineId] start video=$videoId startMs=$startMs")

        playbackJob = scope.launch {
            try {
                val format = playerResponse?.streamingData?.highestQualityFormat
                format?.approxDurationMs?.let { d ->
                    _state.update { it.copy(durationMs = d) }
                    log("pipeline[$pipelineId] duration=${d}ms")
                }

                val ytDlpBin = NativeBinaries.ytDlp()
                val ffmpegBin = NativeBinaries.ffmpeg()
                val url = "https://www.youtube.com/watch?v=$videoId"

                // Name the binaries and their sizes.
                log(
                    "pipeline[$pipelineId] ffmpeg=${File(ffmpegBin).name} " +
                        "at ${File(ffmpegBin).absolutePath} " +
                        "(${(File(ffmpegBin).length() / 1024 / 1024.0).toInt()} MB), " +
                        "yt-dlp at ${File(ytDlpBin).absolutePath} " +
                        "(${(File(ytDlpBin).length() / 1024 / 1024.0).toInt()} MB)"
                )

                val cacheFile = File(cacheDir, videoId)
                val cacheDone = File(cacheDir, "${videoId}.done")

                if (cacheDone.exists() && cacheFile.exists() && cacheFile.length() > 0) {
                    log("pipeline[$pipelineId] cache HIT")
                    val cmd = if (startMs > 0) {
                        listOf(
                            ffmpegBin, "-loglevel", "error",
                            "-ss", (startMs / 1000f).toString(),
                            "-i", cacheFile.absolutePath,
                            "-acodec", "pcm_s16le", "-f", "wav", "-"
                        )
                    } else {
                        listOf(
                            ffmpegBin, "-loglevel", "error",
                            "-i", cacheFile.absolutePath,
                            "-acodec", "pcm_s16le", "-f", "wav", "-"
                        )
                    }
                    val pb = ProcessBuilder(cmd)
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT)
                    decodeProcess = pb.start()
                    when (playViaStream(decodeProcess!!.inputStream, false)) {
                        StreamEnd.COMPLETED -> advanceOrStop()
                        StreamEnd.INCOMPLETE_CACHE -> {
                            log("pipeline[$pipelineId] cache was INCOMPLETE, re-downloading")
                            cacheDone.delete()
                            cacheFile.delete()
                            val song = _state.value.currentSong ?: return@launch
                            playInternal(song)
                        }
                        StreamEnd.INTERRUPTED -> { /* seek/stop handled elsewhere */ }
                    }
                    return@launch
                }


                log("pipeline[$pipelineId] download FULL, tee to cache")
                cacheDir.mkdirs()
                val ytPb = ProcessBuilder(ytDlpBin, "-f", "bestaudio", "-o", "-", "-q", url)
                // INHERIT, not DISCARD. DISCARD threw away the only clue when yt-dlp
                // failed with a 403 and the user saw ffmpeg's downstream complaint instead.
                ytPb.redirectError(ProcessBuilder.Redirect.INHERIT)
                val ytDlp = ytPb.start()
                ytDlpProcess = ytDlp

                val ffPb = ProcessBuilder(
                    ffmpegBin, "-loglevel", "error",

                    *(if (startMs > 0) arrayOf("-ss", (startMs / 1000f).toString()) else emptyArray()),
                    "-i", "-",
                    "-acodec", "pcm_s16le", "-f", "wav", "-"
                )
                ffPb.redirectError(ProcessBuilder.Redirect.INHERIT)
                val ffmpeg = ffPb.start()
                decodeProcess = ffmpeg

                fedBytes.set(0L)
                startTeeThread(ytDlp, ffmpeg, cacheFile, videoId, pipelineId, cache = true)

                try {
                    if (playViaStream(ffmpeg.inputStream, true) == StreamEnd.COMPLETED) {
                        advanceOrStop()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A 403 gives zero bytes, so ffmpeg never opens a stream and
                    // playViaStream throws. Retry the download, not the decode.
                    if (fedBytes.get() == 0L && downloadAttempt < DOWNLOAD_ATTEMPTS - 1) {
                        log("pipeline[$pipelineId] yt-dlp delivered 0 bytes, retry ${downloadAttempt + 1}/$DOWNLOAD_ATTEMPTS")
                        cacheFile.delete()
                        startPipeline(videoId, playerResponse, startMs, downloadAttempt + 1)
                        return@launch
                    }
                    throw e
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // only report error if we're still active pipeline
                if (myGen == currentPipelineGen) {
                    log("pipeline[$pipelineId] error: ${e.message}")
                    fail(e)
                }
            }
        }
    }

    private fun startTeeThread(
        ytDlp: Process,
        ffmpeg: Process,
        cacheFile: File?,
        videoId: String,
        pipelineId: String,
        cache: Boolean
    ) {
        val localCacheFile = cacheFile  // capture local
        Thread {
            val cacheOut = if (cache && localCacheFile != null) {
                runCatching { FileOutputStream(localCacheFile) }.getOrNull()
            } else null

            try {
                ytDlp.inputStream.use { input ->
                    ffmpeg.outputStream.use { output ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = runCatching { input.read(buf) }.getOrNull()
                            if (n == null || n == -1) break
                            fedBytes.addAndGet(n.toLong())
                            runCatching { output.write(buf, 0, n) }
                            runCatching { cacheOut?.write(buf, 0, n) }
                        }
                    }
                }
            } finally {
                runCatching { cacheOut?.close() }
                val fed = fedBytes.get()
                if (fed == 0L) {
                    // exitValue throws if the process has not been reaped yet.
                    val exit = runCatching { ytDlp.exitValue() }.getOrElse { -1 }
                    log("tee[$pipelineId] yt-dlp produced no data, exit=$exit")
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private suspend fun playViaStream(inputStream: java.io.InputStream, isFullDownload: Boolean): StreamEnd =
        withContext(Dispatchers.IO) {
        log("playViaStream start")
        val audioStream = AudioSystem.getAudioInputStream(BufferedInputStream(inputStream))
        stream = audioStream

        val fmt = audioStream.format
        val decodedFormat = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            fmt.sampleRate,
            16,
            fmt.channels,
            fmt.channels * 2,
            fmt.sampleRate,
            false
        )
        bytesPerMs = decodedFormat.sampleRate * decodedFormat.frameSize / 1000.0

        val lineInfo = DataLine.Info(SourceDataLine::class.java, decodedFormat)
        val audioLine = AudioSystem.getLine(lineInfo) as SourceDataLine
        line = audioLine

        audioLine.open(decodedFormat)
        applyVolume()
        audioLine.start()
        log("playViaStream audio ready")

        val decodedStream = AudioSystem.getAudioInputStream(decodedFormat, audioStream)
        val buffer = ByteArray(4096)
        var totalBytes = 0L

        // audio is genuinely flowing, so any earlier failure no longer applies
        _state.update { it.copy(isLoading = false, isPlaying = true, error = null) }
        isPaused = false

        while (isActive) {
            if (isPaused) {
                delay(100)
                continue
            }

            val bytesRead = decodedStream.read(buffer)
            if (bytesRead == -1) break

            audioLine.write(buffer, 0, bytesRead)
            totalBytes += bytesRead

            if (bytesPerMs > 0) {
                _state.update {
                    it.copy(currentPositionMs = seekBaseMs + (totalBytes / bytesPerMs).roundToLong())
                }
            }
        }

        if (audioLine.isOpen) {
            audioLine.drain()
            audioLine.close()
        }
        _state.update { it.copy(isPlaying = false) }

        val completed = isActive && !isPaused
        val finalPos = seekBaseMs + (totalBytes / bytesPerMs).roundToLong()
        val dur = _state.value.durationMs

        val result = when {
            !completed -> {
                val why = if (!isActive) "interrupted (cancelled)" else "paused"
                log("playViaStream end: $why totalBytes=$totalBytes")
                StreamEnd.INTERRUPTED
            }
            !isFullDownload && dur > 0 && finalPos < dur - 5000 -> {
                log("playViaStream end: INCOMPLETE CACHE (pos=$finalPos < dur=$dur)")
                StreamEnd.INCOMPLETE_CACHE
            }
            else -> {
                log("playViaStream end: completed (natural EOF) totalBytes=$totalBytes")
                StreamEnd.COMPLETED
            }
        }

        // if this was a full-song download that completed naturally, mark cache as done.
        if (completed && isFullDownload) {
            val cacheDone = File(cacheDir, "${currentVideoId}.done")
            if (cacheDone.parentFile.isDirectory) {
                runCatching { cacheDone.createNewFile() }
                log("cache DONE: $currentVideoId")
            }
        }

        return@withContext result
    }

    private fun endSong() {
        playbackJob?.cancel()
        playbackJob = null
        stopAudio()
        _state.update {
            it.copy(
                isPlaying = false,
                isEnded = true,
                isLoading = false,
                currentPositionMs = it.durationMs
            )
        }
        log("endSong")
    }

    private fun advanceOrStop() {
        val s = _state.value
        val nextAction = when (s.loopMode) {
            LoopMode.NONE -> if (s.currentIndex + 1 < s.queue.size) "next-in-queue" else "endSong"
            LoopMode.ONE -> "replay"
            LoopMode.ALL -> "wrap-to-start"
        }
        log("advanceOrStop: action=$nextAction loopMode=${s.loopMode}")
        when (s.loopMode) {
            LoopMode.NONE -> {
                val next = s.currentIndex + 1
                if (next < s.queue.size) {
                    _state.update { it.copy(currentIndex = next) }
                    maybeSaveQueue()
                    playInternal(s.queue[next])
                } else {
                    endSong()
                }
            }
            LoopMode.ONE -> {
                s.currentSong?.let { playInternal(it) }
            }
            LoopMode.ALL -> {
                val next = (s.currentIndex + 1) % s.queue.size
                _state.update { it.copy(currentIndex = next) }
                maybeSaveQueue()
                playInternal(s.queue[next])
            }
        }
    }

    private fun maybeSaveQueue() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        QueueDatabase.save(
            app.pulse.core.data.models.PlaybackState(
                queue = s.queue,
                currentIndex = s.currentIndex,
                loopMode = s.loopMode,
                volume = s.volume,
                currentPositionMs = s.currentPositionMs,
                durationMs = s.durationMs
            )
        )
        log("queue saved (${s.queue.size} items, idx=${s.currentIndex}, pos=${s.currentPositionMs}ms)")
    }

    private fun maybeRestoreQueue() {
        val saved = QueueDatabase.restore() ?: return
        QueueDatabase.clear()

        val songs = mutableListOf<Song>()
        saved.queue.forEach { entry ->
            try {
                val song = QueueDatabase.json.decodeFromString<Song>(entry.songJson)
                songs.add(song)
            } catch (e: Exception) {
                log("restore: failed to deserialize ${entry.videoId}: ${e.message}")
            }
        }

        if (songs.isEmpty()) return

        log("restore: ${songs.size} songs, index=${saved.currentIndex}, pos=${saved.positionMs}ms")

        _state.update {
            PlaybackState(
                queue = songs,
                currentIndex = saved.currentIndex.coerceIn(0, songs.lastIndex),
                loopMode = saved.loopMode,
                volume = saved.volume,
                durationMs = saved.durationMs
            )
        }

        // Restore song in paused state user presses play to resume
        val song = songs.getOrNull(saved.currentIndex) ?: return
        _state.update {
            it.copy(
                currentSong = song,
                currentPositionMs = saved.positionMs,
                isPlaying = false,
                isEnded = false,
                isLoading = false
            )
        }
        log("restore: song='${song.title}' paused at ${saved.positionMs}ms")
    }

    private fun stopAudio() {
        runCatching { decodeProcess?.destroyForcibly() }
        decodeProcess = null
        runCatching { ytDlpProcess?.destroyForcibly() }
        ytDlpProcess = null
        runCatching { line?.stop() }
        runCatching { line?.close() }
        runCatching { stream?.close() }
        line = null
        stream = null
        isPaused = false
    }

    fun dispose() {
        stop()
        scope.cancel()
        QueueDatabase.close()
        log("dispose")
    }
}
