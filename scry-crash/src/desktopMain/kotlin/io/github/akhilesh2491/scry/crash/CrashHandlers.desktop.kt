package io.github.akhilesh2491.scry.crash

import io.github.akhilesh2491.scry.core.PlatformContext

internal actual fun nowMillis(): Long = System.currentTimeMillis()

internal actual fun installCrashHandlers(
    context: PlatformContext,
    plugin: CrashPlugin,
): AutoCloseable? {
    if (!plugin.config.captureCrashes) return null

    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching {
            plugin.record(plugin.buildRecord(CrashKind.CRASH, thread.name, throwable))
        }
        previous?.uncaughtException(thread, throwable)
    }
    // No ANR watchdog on desktop: there is no system "application not responding"
    // concept to mirror, and a frozen Swing EDT is already visible to the user.
    return AutoCloseable { Thread.setDefaultUncaughtExceptionHandler(previous) }
}
