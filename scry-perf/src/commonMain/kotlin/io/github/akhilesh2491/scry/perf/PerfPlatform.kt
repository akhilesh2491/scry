package io.github.akhilesh2491.scry.perf

import io.github.akhilesh2491.scry.core.PlatformContext

/** What the app is running on, recorded with every session so numbers are comparable. */
public data class PerfPlatform(
    public val name: String,
    public val deviceModel: String? = null,
    public val appVersion: String? = null,
)

/**
 * Wall-clock time, epoch millis. For timestamps only — never for durations.
 *
 * `scry-core` has an identical `currentTimeMillis()`, but it is `internal` to
 * that module; `scry-network`, `scry-logs` and `scry-crash` each declare their
 * own copy for the same reason. Sharing one would mean widening core's API.
 */
internal expect fun nowMillis(): Long

/**
 * A clock that only moves forward, in millis, with an arbitrary origin.
 *
 * The only clock that may be subtracted. On Android this is deliberately
 * `SystemClock.uptimeMillis()` and **not** `System.nanoTime()`: nanoTime keeps
 * counting through deep sleep, so subtracting it from
 * `Process.getStartUptimeMillis()` would report a startup of several hours for
 * an app launched the morning after it was installed.
 */
internal expect fun monotonicMillis(): Long

/**
 * When this process started, on the [monotonicMillis] scale.
 *
 * Null when the platform cannot tell us — Android below API 24, and iOS, where
 * the Kotlin framework only becomes able to observe anything well after `main`.
 * Callers fall back to Scry's own load time and mark the metric truncated.
 */
internal expect fun processStartMillis(): Long?

/** Device and app identification for the session record. */
internal expect fun perfPlatform(context: PlatformContext): PerfPlatform
