@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.akhilesh2491.scry.crash

import io.github.akhilesh2491.scry.core.PlatformContext
import kotlinx.cinterop.staticCFunction
import platform.Foundation.NSDate
import platform.Foundation.NSException
import platform.Foundation.NSSetUncaughtExceptionHandler
import platform.Foundation.timeIntervalSince1970

internal actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

/**
 * Installs an uncaught Objective-C exception handler.
 *
 * Two honest limitations, both inherent to the platform rather than to Scry:
 *
 * - `NSSetUncaughtExceptionHandler` takes a C function pointer, so the handler
 *   cannot capture state. The plugin is reached through a file-level reference
 *   set just before installation.
 * - It sees Objective-C exceptions and Kotlin exceptions that reach the top of
 *   a thread, but **not** fatal signals (SIGSEGV, SIGABRT) — which is how most
 *   Kotlin/Native crashes actually terminate. Catching those needs a signal
 *   handler that is unsafe to run arbitrary code from. For real production
 *   crash reporting on iOS, keep using a dedicated reporter; Scry is here for
 *   the debug loop.
 *
 * There is no ANR watchdog: iOS has no equivalent system concept, and the
 * watchdog that does exist (the springboard's) kills the app outright.
 */
internal actual fun installCrashHandlers(
    context: PlatformContext,
    plugin: CrashPlugin,
): AutoCloseable? {
    if (!plugin.config.captureCrashes) return null

    activePlugin = plugin
    NSSetUncaughtExceptionHandler(
        staticCFunction { exception: NSException? -> reportUncaught(exception) },
    )

    return AutoCloseable {
        activePlugin = null
        NSSetUncaughtExceptionHandler(null)
    }
}

/**
 * The body of the C handler.
 *
 * A top-level function, not a lambda: `staticCFunction` requires a pointer to
 * code that captures nothing, so the plugin has to be reached through
 * [activePlugin] rather than closed over.
 */
private fun reportUncaught(exception: NSException?) {
    val current = activePlugin ?: return
    runCatching {
        current.record(
            current.buildRecord(
                kind = CrashKind.CRASH,
                threadName = "unknown",
                throwable = null,
                overrideStackTrace = buildString {
                    appendLine(exception?.name ?: "NSException")
                    exception?.reason?.let { appendLine(it) }
                    appendLine()
                    exception?.callStackSymbols
                        ?.filterIsInstance<String>()
                        ?.forEach { appendLine("\t$it") }
                },
            ),
        )
    }
}

/**
 * The plugin the C handler reports to.
 *
 * A file-level reference because the handler is a C function pointer and cannot
 * close over anything.
 */
private var activePlugin: CrashPlugin? = null
