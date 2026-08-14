package io.github.akhilesh2491.scry.perf

import io.github.akhilesh2491.scry.core.PlatformContext
import java.lang.management.ManagementFactory

internal actual fun nowMillis(): Long = System.currentTimeMillis()

internal actual fun monotonicMillis(): Long = System.nanoTime() / 1_000_000

/**
 * JVM start, converted onto the [monotonicMillis] scale.
 *
 * `RuntimeMXBean.getStartTime()` is wall-clock epoch millis, so it is rebased
 * through the current instant rather than returned directly — the two clocks
 * have unrelated origins and subtracting across them is meaningless.
 */
internal actual fun processStartMillis(): Long? = runCatching {
    val uptimeMillis = ManagementFactory.getRuntimeMXBean().uptime
    monotonicMillis() - uptimeMillis
}.getOrNull()

internal actual fun perfPlatform(context: PlatformContext): PerfPlatform = PerfPlatform(
    name = "${System.getProperty("os.name")} (JVM ${System.getProperty("java.version")})",
    deviceModel = System.getProperty("os.arch"),
    appVersion = context.applicationId,
)
