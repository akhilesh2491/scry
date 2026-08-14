package io.github.akhilesh2491.scry.perf

import io.github.akhilesh2491.scry.core.PlatformContext

/**
 * Desktop captures no frames, deliberately.
 *
 * The only mechanism available in Compose for Desktop is a perpetual
 * `withFrameNanos` loop, which invalidates every frame and so *causes* the
 * rendering it claims to observe — the measurement would be of Scry, not of the
 * app. Rather than publish a number that is wrong in a way nobody can see, this
 * returns null and the plugin's screen says frames are not captured here.
 *
 * Startup and manual spans work normally; screen loads need
 * [ScryTrace.screenDisplayed] rather than [ScryTrace.screenEntered], because
 * there is no frame to close the measurement.
 */
internal actual fun startPerfMonitor(context: PlatformContext, plugin: PerfPlugin): AutoCloseable? {
    plugin.markFramesUnsupported(
        "Frame timing is not captured on desktop: the only mechanism available " +
            "would force a recomposition every frame and measure itself.",
    )

    if (!plugin.config.trackStartup) return null

    processStartMillis()?.let { start ->
        plugin.recordStartup(
            StartupMetric(
                kind = StartupKind.COLD,
                totalMillis = monotonicMillis() - start,
                timestampMillis = nowMillis(),
                phases = listOf(
                    PerfPhase("JVM start → Scry install", 0, monotonicMillis() - start),
                ),
                // The JVM has started, but the app's first window has not
                // necessarily been shown, so this is not comparable to a mobile
                // time-to-first-frame.
                truncated = true,
            ),
        )
    }
    return null
}
