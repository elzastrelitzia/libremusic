package app.pulse.desktop.ui.utils

import java.io.File
import java.util.concurrent.TimeUnit

private fun mb(bytes: Long) = "%.0f MB".format(bytes / 1048576.0)

/** Windows has no /proc and the JDK exposes no system-RAM API, so spend one slow
 *  subprocess at startup instead of shipping JNA for a single number. */
private fun windowsMemory(): Pair<Long, Long>? = runCatching {
    // $PID inside PowerShell is PowerShell's own pid, so pass ours in explicitly.
    val script = "\$m=(Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory;" +
        "\$p=(Get-Process -Id ${ProcessHandle.current().pid()}).WorkingSet64;" +
        "Write-Output \"\$m \$p\""
    val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
        .redirectErrorStream(true)
        .start()
    if (!p.waitFor(10, TimeUnit.SECONDS)) {
        p.destroyForcibly()
        return@runCatching null
    }
    val nums = p.inputStream.bufferedReader().readText().trim().split(Regex("\\s+"))
    if (nums.size < 2) return@runCatching null
    nums[0].toLong() to nums[1].toLong()
}.getOrNull()

/** kB, from the first line whose field name matches, e.g. MemTotal or VmRSS.
 *  /proc values carry a "kB" suffix, so keep only the leading number. */
private fun procKb(path: String, field: String): Long? = runCatching {
    File(path).useLines { lines ->
        lines.firstOrNull { it.startsWith("$field:") }
            ?.substringAfter(':')?.trim()?.substringBefore(' ')?.toLong()
    }
}.getOrNull()

/** Log host specs and this process's own footprint. Cheap parts first, then the
 *  slow platform query off the UI thread so the window is not delayed. */
fun logSystemInfo() {
    val rt = Runtime.getRuntime()
    val heapUsed = rt.totalMemory() - rt.freeMemory()
    val isWindows = System.getProperty("os.name").lowercase().contains("win")

    log(
        "Sys",
        "os=${System.getProperty("os.name")} ${System.getProperty("os.version")} " +
            "arch=${System.getProperty("os.arch")} cpus=${rt.availableProcessors()}"
    )
    log(
        "Sys",
        "jvm=${System.getProperty("java.vm.name")} ${System.getProperty("java.version")} " +
            "heap used=${mb(heapUsed)} committed=${mb(rt.totalMemory())} max=${mb(rt.maxMemory())}"
    )
    log(
        "Sys",
        "app rss=" + (processRss()?.let { mb(it) } ?: "unavailable") +
            // Thread.activeCount() only counts live Java threads, which is 1 this early.
            // /proc counts every OS thread the JVM has started.
            " threads=" + (procKb("/proc/self/status", "Threads") ?: Thread.activeCount().toString())
    )

    if (isWindows) {
        Thread {
            windowsMemory()?.let { (total, rss) ->
                log("Sys", "system ram=${mb(total)} app rss=${mb(rss)} (powershell)")
            } ?: log("Sys", "system ram=unavailable (powershell failed)")
        }.apply { isDaemon = true }.start()
    } else {
        val total = procKb("/proc/meminfo", "MemTotal")
        val free = procKb("/proc/meminfo", "MemAvailable")
        log(
            "Sys",
            "system ram=" + (total?.let { mb(it * 1024) } ?: "unavailable") +
                " free=" + (free?.let { mb(it * 1024) } ?: "unavailable")
        )
    }
}

/** Resident set of this process. Null where we cannot read it. */
private fun processRss(): Long? = procKb("/proc/self/status", "VmRSS")?.times(1024)
