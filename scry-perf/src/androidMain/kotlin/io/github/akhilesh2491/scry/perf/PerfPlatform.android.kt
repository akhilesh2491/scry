package io.github.akhilesh2491.scry.perf

import android.os.Build
import android.os.Process
import android.os.SystemClock
import io.github.akhilesh2491.scry.core.PlatformContext

internal actual fun nowMillis(): Long = System.currentTimeMillis()

/**
 * `SystemClock.uptimeMillis()`, not `System.nanoTime()`.
 *
 * The two are not interchangeable here: nanoTime keeps counting while the device
 * is in deep sleep, and this value is subtracted from
 * [Process.getStartUptimeMillis], which does not. Mixing them reports a startup
 * time as long as the phone has been sitting in a pocket.
 */
internal actual fun monotonicMillis(): Long = SystemClock.uptimeMillis()

/**
 * True process start on API 24+.
 *
 * Below that there is no supported way to learn it — the alternatives are a
 * `ContentProvider` that runs before `Application.onCreate` (which would add
 * startup cost to every launch of an app that installed a tool for measuring
 * startup cost) or reading `/proc`, which is not a stable contract. API 23 gets
 * a truncated measurement, clearly labelled as one.
 */
internal actual fun processStartMillis(): Long? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Process.getStartUptimeMillis() else null

internal actual fun perfPlatform(context: PlatformContext): PerfPlatform {
    val android = context.androidContext
    val versionName = runCatching {
        @Suppress("DEPRECATION")
        android.packageManager.getPackageInfo(android.packageName, 0).versionName
    }.getOrNull()

    return PerfPlatform(
        name = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
        appVersion = versionName,
    )
}
