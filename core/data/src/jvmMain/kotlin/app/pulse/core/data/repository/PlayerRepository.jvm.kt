package app.pulse.core.data.repository

import app.pulse.core.data.models.PlaybackState
import app.pulse.core.data.models.Song
import app.pulse.core.data.utils.NativeBinaries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.io.InputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10
import kotlin.math.roundToLong

class PlayerRepositoryImpl : PlayerRepository {
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var playbackJob: Job? = null
    private var line: SourceDataLine? = null
    private var decodeProcess: Process? = null
    private var ytDlpProcess: Process? = null

    private var isPaused = false
    private var seekBaseMs = 0L
    private var currentPipelineGen = 0L

    override suspend fun play(song: Song) {
        stop()
        currentPipelineGen++
        val myGen = currentPipelineGen

        _state.update { it.copy(currentSong = song, isPlaying = true) }

        playbackJob = scope.launch {
            try {
                startPipeline(song.id, myGen)
            } catch (e: Exception) {
                if (myGen == currentPipelineGen) {
                    _state.update { it.copy(isPlaying = false) }
                }
            }
        }
    }

    private suspend fun startPipeline(videoId: String, myGen: Long, startMs: Long = 0L) {
        stopAudio()
        seekBaseMs = startMs

        val ytDlpBin = NativeBinaries.ytDlp()
        val ffmpegBin = NativeBinaries.ffmpeg()
        val url = "https://www.youtube.com/watch?v=$videoId"

        val ytPb = ProcessBuilder(ytDlpBin, "-f", "bestaudio", "-o", "-", "-q", url)
        val ytDlp = ytPb.start()
        ytDlpProcess = ytDlp

        val ffPb = ProcessBuilder(
            ffmpegBin, "-loglevel", "error", "-i", "-",
            "-acodec", "pcm_s16le", "-f", "wav", "-"
        )
        val ffmpeg = ffPb.start()
        decodeProcess = ffmpeg

        startTeeThread(ytDlp, ffmpeg)

        playViaStream(ffmpeg.inputStream)
    }

    private fun startTeeThread(ytDlp: Process, ffmpeg: Process) {
        Thread {
            try {
                ytDlp.inputStream.use { input ->
                    ffmpeg.outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (_: Exception) {}
        }.apply { isDaemon = true }.start()
    }

    private suspend fun playViaStream(inputStream: InputStream) = withContext(Dispatchers.IO) {
        val audioStream = AudioSystem.getAudioInputStream(BufferedInputStream(inputStream))
        val fmt = audioStream.format
        val decodedFormat = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            fmt.sampleRate, 16, fmt.channels, fmt.channels * 2, fmt.sampleRate, false
        )
        val bytesPerMs = decodedFormat.sampleRate * decodedFormat.frameSize / 1000.0

        val lineInfo = DataLine.Info(SourceDataLine::class.java, decodedFormat)
        val audioLine = AudioSystem.getLine(lineInfo) as SourceDataLine
        line = audioLine
        audioLine.open(decodedFormat)
        audioLine.start()

        val decodedStream = AudioSystem.getAudioInputStream(decodedFormat, audioStream)
        val buffer = ByteArray(4096)
        var totalBytes = 0L

        while (isActive) {
            if (isPaused) {
                delay(100)
                continue
            }
            val n = decodedStream.read(buffer)
            if (n == -1) break
            audioLine.write(buffer, 0, n)
            totalBytes += n
            _state.update { it.copy(currentPositionMs = seekBaseMs + (totalBytes / bytesPerMs).roundToLong()) }
        }

        audioLine.drain()
        audioLine.close()
    }

    override suspend fun pause() {
        isPaused = true
        line?.stop()
        _state.update { it.copy(isPlaying = false) }
    }

    override suspend fun resume() {
        isPaused = false
        line?.start()
        _state.update { it.copy(isPlaying = true) }
    }

    override suspend fun stop() {
        playbackJob?.cancel()
        stopAudio()
        _state.update { it.copy(isPlaying = false, currentSong = null) }
    }

    override suspend fun seekTo(positionMs: Long) {
        val song = _state.value.currentSong ?: return
        currentPipelineGen++
        val myGen = currentPipelineGen
        playbackJob = scope.launch {
            startPipeline(song.id, myGen, positionMs)
        }
    }

    override suspend fun setVolume(volume: Float) {
        val audioLine = line ?: return
        val gain = audioLine.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl ?: return
        val db = if (volume <= 0f) gain.minimum else (20f * log10(volume)).coerceIn(gain.minimum, gain.maximum)
        gain.value = db
    }

    private fun stopAudio() {
        decodeProcess?.destroyForcibly()
        ytDlpProcess?.destroyForcibly()
        line?.stop()
        line?.close()
        isPaused = false
    }
}
