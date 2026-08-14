package io.github.akhilesh2491.scry.logs

import io.github.akhilesh2491.scry.core.PlatformContext
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

/**
 * iOS has no readable per-process log stream.
 *
 * `os_log` writes into a system store that an app cannot read back for itself,
 * so there is nothing to tail. Use [ScryLog] (or route your existing logger into
 * it) instead of `captureSystemLog`.
 */
internal actual fun startSystemLogCapture(
    context: PlatformContext,
    plugin: LogPlugin,
): AutoCloseable? = null
