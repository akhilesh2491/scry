package io.github.akhilesh2491.scry.perf

import io.github.akhilesh2491.scry.core.PlatformContext
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSProcessInfo
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIDevice

internal actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

/** `systemUptime` is monotonic and, like Android's uptime, excludes deep sleep. */
internal actual fun monotonicMillis(): Long =
    (NSProcessInfo.processInfo.systemUptime * 1000).toLong()

/**
 * Always null.
 *
 * By the time Kotlin/Native code can run, `main` has already returned into the
 * run loop and the expensive part of an iOS launch — dyld, ObjC class
 * registration, static initialisers — is over. Reporting the framework's own
 * load time as "process start" would produce a flattering number that means
 * nothing, so startup on iOS is measured from a point Scry can actually see and
 * marked truncated.
 */
internal actual fun processStartMillis(): Long? = null

internal actual fun perfPlatform(context: PlatformContext): PerfPlatform {
    val device = UIDevice.currentDevice
    val version = NSBundle.mainBundle.infoDictionary
        ?.get("CFBundleShortVersionString") as? String

    return PerfPlatform(
        name = "${device.systemName} ${device.systemVersion}",
        deviceModel = device.model,
        appVersion = version,
    )
}
