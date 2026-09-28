package app.pulse.desktop.ui.utils

import app.pulse.core.data.utils.AppDirs
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val logFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

fun log(tag: String, msg: String) = println("[${LocalTime.now().format(logFmt)}] [$tag] $msg")

private class Tee(private val console: OutputStream, private val file: OutputStream) : OutputStream() {
    override fun write(b: Int) {
        console.write(b)
        file.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        console.write(b, off, len)
        file.write(b, off, len)
    }

    override fun flush() {
        console.flush()
        file.flush()
    }
}

/**
 * Mirror stdout into ~/.libremusic/log.txt. Redirecting the stream rather than
 * writing the file inside log() so that stack traces and anything the libraries
 * print on their own are captured too, not just our own tagged lines.
 *
 * ponytail: no rotation, about 10 kB per run. Cap it only if it ever matters.
 */
fun startFileLogging() {
    val file = AppDirs.log
    val console = System.out
    val stream = runCatching {
        PrintStream(
            Tee(console, BufferedOutputStream(FileOutputStream(file, true))),
            /* autoFlush = */ true
        )
    }.getOrNull() ?: return
    System.setOut(stream)
    System.setErr(stream)
    log("Logger", "log opened at $file")
}
