package io.github.akhilesh2491.scry.crash

import android.os.Handler
import android.os.Looper
import io.github.akhilesh2491.scry.core.PlatformContext
import java.util.concurrent.atomic.AtomicBoolean

internal actual fun nowMillis(): Long = System.currentTimeMillis()

internal actual fun installCrashHandlers(
    context: PlatformContext,
    plugin: CrashPlugin,
): AutoCloseable? {
    val closers = mutableListOf<AutoCloseable>()

    if (plugin.config.captureCrashes) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val handler = Thread.UncaughtExceptionHandler { thread, throwable ->
            // Record first, then always delegate. Swallowing the exception would
            // break Crashlytics and suppress the system's own crash dialog —
            // Scry must be additive, never a replacement.
            runCatching {
                plugin.record(plugin.buildRecord(CrashKind.CRASH, thread.name, throwable))
            }
            previous?.uncaughtException(thread, throwable)
        }
        Thread.setDefaultUncaughtExceptionHandler(handler)
        closers += AutoCloseable { Thread.setDefaultUncaughtExceptionHandler(previous) }
    }

    if (plugin.config.captureAnrs) {
        val watchdog = AnrWatchdog(plugin)
        watchdog.start()
        closers += AutoCloseable { watchdog.stop() }
    }

    return AutoCloseable { closers.forEach { runCatching { it.close() } } }
}

/**
 * Detects main-thread stalls by asking it to reply.
 *
 * Posts a token to the main looper and waits. If the main thread has not cleared
 * the token within the threshold, it is blocked, and its stack — captured from
 * *this* thread while it is still stuck — is what you actually need. Reading the
 * stack after the block clears would show nothing useful.
 */
internal class AnrWatchdog(
    private val plugin: CrashPlugin,
    private val pollIntervalMillis: Long = 1_000L,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread({ loop() }, "scry-anr-watchdog").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        thread?.interrupt()
        thread = null
    }

    private fun loop() {
        val thresholdMillis = plugin.config.anrThreshold.inWholeMilliseconds
        var alreadyReported = false

        while (running.get()) {
            val responded = AtomicBoolean(false)
            mainHandler.post { responded.set(true) }

            val deadline = System.currentTimeMillis() + thresholdMillis
            while (running.get() && !responded.get() && System.currentTimeMillis() < deadline) {
                runCatching { Thread.sleep(50) }.onFailure { return }
            }

            if (!responded.get()) {
                // Report once per stall, not once per poll — a 30-second block
                // should produce one report, not thirty.
                if (!alreadyReported) {
                    alreadyReported = true
                    runCatching { reportAnr(thresholdMillis) }
                }
            } else {
                alreadyReported = false
            }

            runCatching { Thread.sleep(pollIntervalMillis) }.onFailure { return }
        }
    }

    private fun reportAnr(thresholdMillis: Long) {
        val mainThread = Looper.getMainLooper().thread
        val stack = mainThread.stackTrace.joinToString("\n") { "\tat $it" }
        plugin.record(
            plugin.buildRecord(
                kind = CrashKind.ANR,
                threadName = mainThread.name,
                throwable = null,
                overrideStackTrace =
                "Main thread unresponsive for more than ${thresholdMillis}ms\n\n$stack",
            ),
        )
    }
}
