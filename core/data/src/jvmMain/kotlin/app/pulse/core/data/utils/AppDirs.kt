package app.pulse.core.data.utils

import java.io.File

object AppDirs {
    val root: File = File(System.getProperty("user.home"), ".libremusic").also { it.mkdirs() }

    val audio: File = dir("cache")
    val images: File = dir("image_cache")
    val home: File = dir("home_cache")
    val native: File = dir("native")

    val queueDb: File = File(root, "queue.db")
    val log: File = File(root, "log.txt")

    private fun dir(name: String) = File(root, name).also { it.mkdirs() }
}
