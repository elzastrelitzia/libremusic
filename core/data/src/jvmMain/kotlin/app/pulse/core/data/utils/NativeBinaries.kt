package app.pulse.core.data.utils

import java.io.File
import java.util.Locale

/**
 * Resolves bundled native binary paths for yt-dlp and ffmpeg.
 */
object NativeBinaries {

    private val currentOs: String get() {
        val name = System.getProperty("os.name").lowercase(Locale.ROOT)
        return when {
            name.contains("win") -> "windows"
            name.contains("mac") || name.contains("darwin") -> "macos"
            else -> "linux"
        }
    }

    /** Returns current platform: "linux", "macos", or "windows". */
    fun currentOsName(): String = currentOs

    /** Appends .exe on Windows. */
    private fun exe(name: String): String = if (currentOs == "windows") "$name.exe" else name

    /** Resolve path to yt-dlp binary. */
    fun ytDlp(): String = resolve("yt-dlp")

    /** Resolve path to ffmpeg binary. */
    fun ffmpeg(): String = resolve("ffmpeg")

    /** True if resolved path is a bundled binary (not system PATH fallback). */
    fun isBundled(name: String): Boolean {
        val resolved = resolve(name)
        val f = File(resolved)
        return f.parent != "." && resolved != name && resolved != exe(name)
    }

    /** Resolve path to any native binary. */
    fun resolve(name: String): String {
        val osDir = currentOs
        val binName = exe(name)

        // 1. System property override (platform-agnostic)
        val overrideDir = System.getProperty("pulse.native.dir")
        if (overrideDir != null) {
            val f = File(overrideDir, binName)
            if (isExecutable(f)) return f.absolutePath
        }

        // 2. ./native/<os>/<name> relative to working dir
        val local = File("native/$osDir/$binName")
        if (isExecutable(local)) return local.absolutePath

        // 3. JAR resources /native/<os>/<name>
        val resourcePath = "/native/$osDir/$binName"
        val resourceUrl = NativeBinaries::class.java.getResource(resourcePath)
            ?: NativeBinaries::class.java.classLoader.getResource(resourcePath.removePrefix("/"))

        if (resourceUrl != null) {
            if (resourceUrl.protocol == "file") {
                val resourceFile = File(resourceUrl.toURI())
                if (isExecutable(resourceFile)) return resourceFile.absolutePath
            }
            return extractFromJar(resourcePath, binName)
        }

        // 4. System PATH fallback
        return binName
    }

    private val extractLock = Any()

    private fun extractFromJar(resourcePath: String, binName: String): String {
        synchronized(extractLock) {
            val extracted = File(AppDirs.native, binName)

            if (!extracted.exists()) {
                try {
                    val input = NativeBinaries::class.java.getResourceAsStream(resourcePath)
                        ?: NativeBinaries::class.java.classLoader.getResourceAsStream(resourcePath.removePrefix("/"))

                    input?.use {
                        extracted.outputStream().use { output ->
                            it.copyTo(output)
                        }
                    }
                    extracted.setExecutable(true)
                } catch (_: Exception) {
                    return binName
                }
            }

            if (isExecutable(extracted)) return extracted.absolutePath
            return binName
        }
    }

    private fun isExecutable(f: File): Boolean {
        if (!f.exists()) return false
        if (currentOs == "windows") return f.name.endsWith(".exe")
        return f.canExecute()
    }
}
