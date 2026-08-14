package io.github.akhilesh2491.scry.logs

import io.github.akhilesh2491.scry.core.PlatformContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tails logcat for this process.
 *
 * Since Android 4.1 an app can only read its own log, which is exactly the scope
 * wanted here — `--pid` makes that explicit rather than relying on the platform
 * to filter. Runs on a daemon thread so it can never hold up process exit.
 */
internal actual fun startSystemLogCapture(
    context: PlatformContext,
    plugin: LogPlugin,
): AutoCloseable? {
    val running = AtomicBoolean(true)
    var process: java.lang.Process? = null

    val thread = Thread({
        try {
            val started = ProcessBuilder(
                "logcat",
                "--pid=${android.os.Process.myPid()}",
                "-v", "threadtime",
            ).redirectErrorStream(true).start()
            process = started

            BufferedReader(InputStreamReader(started.inputStream)).use { reader ->
                while (running.get()) {
                    val line = reader.readLine() ?: break
                    parseLogcatLine(line)?.let { (level, tag, message) ->
                        plugin.record(level, tag, message, source = LogSource.PLATFORM)
                    }
                }
            }
        } catch (_: Exception) {
            // Reading logcat is best-effort. A device that refuses is a missing
            // feature, never a crash in the host app.
        }
    }, "scry-logcat").apply {
        isDaemon = true
        start()
    }

    return AutoCloseable {
        running.set(false)
        runCatching { process?.destroy() }
        thread.interrupt()
    }
}

/**
 * Parses a `threadtime` logcat line.
 *
 * Format: `MM-DD HH:MM:SS.mmm  PID  TID L TAG: message`
 * Returns null for continuation lines and anything unrecognised, which is
 * preferable to inventing a tag for output that does not have one.
 */
internal fun parseLogcatLine(line: String): Triple<LogLevel, String, String>? {
    val match = LOGCAT_PATTERN.matchEntire(line) ?: return null
    val (levelChar, tag, message) = match.destructured
    val level = when (levelChar) {
        "V" -> LogLevel.VERBOSE
        "D" -> LogLevel.DEBUG
        "I" -> LogLevel.INFO
        "W" -> LogLevel.WARN
        "E" -> LogLevel.ERROR
        "F", "A" -> LogLevel.ASSERT
        else -> return null
    }
    return Triple(level, tag.trim(), message)
}

private val LOGCAT_PATTERN =
    Regex("""^\d{2}-\d{2} [\d:.]+\s+\d+\s+\d+ ([VDIWEFA]) (.+?): (.*)$""")
