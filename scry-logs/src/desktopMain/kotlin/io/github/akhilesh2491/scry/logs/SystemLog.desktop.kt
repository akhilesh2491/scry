package io.github.akhilesh2491.scry.logs

import io.github.akhilesh2491.scry.core.PlatformContext

/**
 * Desktop has no per-process log stream to tail.
 *
 * Its stdout is already visible in the terminal that launched the app, so
 * duplicating it into Scry would add noise rather than insight.
 */
internal actual fun startSystemLogCapture(
    context: PlatformContext,
    plugin: LogPlugin,
): AutoCloseable? = null
