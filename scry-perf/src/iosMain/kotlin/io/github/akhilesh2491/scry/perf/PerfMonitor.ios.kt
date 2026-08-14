package io.github.akhilesh2491.scry.perf

import io.github.akhilesh2491.scry.core.PlatformContext
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSSelectorFromString
import platform.QuartzCore.CADisplayLink
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.darwin.NSObject

/**
 * iOS instrumentation: `CADisplayLink` for frames, app lifecycle for startup.
 *
 * What the frame numbers mean here is narrower than on Android, and the docs say
 * so rather than letting the two look interchangeable. `CADisplayLink` fires once
 * per display refresh from the main run loop; it does not render anything and it
 * cannot see the GPU. What a long interval between callbacks proves is that the
 * **main thread was blocked** — which is what iOS jank almost always is. GPU-side
 * stalls are invisible to it.
 *
 * Unlike the desktop case this is still worth capturing: the link observes the
 * run loop rather than driving a render, so it does not manufacture the frames it
 * reports. It does keep the CPU waking every vsync, which is why `trackFrames`
 * turns it off.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun startPerfMonitor(context: PlatformContext, plugin: PerfPlugin): AutoCloseable? =
    IosPerfMonitor(plugin).also { it.start() }

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosPerfMonitor(private val plugin: PerfPlugin) : AutoCloseable {

    private val startup = StartupTracker(
        // iOS cannot report a usable process start — see processStartMillis.
        processStartMillis = null,
        installMillis = monotonicMillis(),
    )

    private var displayLink: CADisplayLink? = null
    private var target: DisplayLinkTarget? = null
    private var becameActiveObserver: Any? = null
    private var willResignObserver: Any? = null

    fun start() {
        // The Kotlin framework loads long after the process does, so treat "Scry
        // was installed" as the earliest observable point and let the tracker
        // mark the resulting metric truncated.
        if (plugin.config.trackStartup) {
            startup.onActivityCreated(hasSavedState = false, nowMillis = monotonicMillis())
        }

        val centre = NSNotificationCenter.defaultCenter

        becameActiveObserver = centre.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            if (plugin.config.trackStartup) {
                val now = monotonicMillis()
                startup.onActivityStarted(now)
                startup.onFirstFrame(now)?.let(plugin::recordStartup)
            }
            if (plugin.config.trackFrames) startDisplayLink()
        }

        willResignObserver = centre.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            if (plugin.config.trackStartup) startup.onActivityStopped()
            stopDisplayLink()
        }
    }

    override fun close() {
        stopDisplayLink()
        val centre = NSNotificationCenter.defaultCenter
        becameActiveObserver?.let { centre.removeObserver(it) }
        willResignObserver?.let { centre.removeObserver(it) }
        becameActiveObserver = null
        willResignObserver = null
    }

    private fun startDisplayLink() {
        if (displayLink != null) return

        val linkTarget = DisplayLinkTarget(plugin)
        val link = CADisplayLink.displayLinkWithTarget(
            target = linkTarget,
            selector = NSSelectorFromString("onFrame:"),
        )
        link.addToRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)

        target = linkTarget
        displayLink = link
    }

    private fun stopDisplayLink() {
        displayLink?.invalidate()
        displayLink = null
        target = null
    }
}

/**
 * The `CADisplayLink` callback target.
 *
 * `CADisplayLink` needs an Objective-C target/selector pair, so this has to be an
 * `NSObject` subclass with an `@ObjCAction` method — there is no block-based
 * alternative on the API.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class DisplayLinkTarget(private val plugin: PerfPlugin) : NSObject() {

    private var lastTimestamp: Double = 0.0

    @ObjCAction
    fun onFrame(link: CADisplayLink) {
        val timestamp = link.timestamp

        if (lastTimestamp != 0.0) {
            val budgetMillis = (link.duration * MILLIS_PER_SECOND)
                .takeIf { it > 0.0 } ?: DEFAULT_FRAME_BUDGET_MILLIS
            plugin.recordFrame(
                screen = plugin.currentScreen,
                durationMillis = (timestamp - lastTimestamp) * MILLIS_PER_SECOND,
                budgetMillis = budgetMillis,
            )
        }

        lastTimestamp = timestamp
    }
}

// Top-level rather than in a companion: Kotlin/Native does not allow fields on
// the companion of an Objective-C subclass.
private const val MILLIS_PER_SECOND = 1_000.0
private const val DEFAULT_FRAME_BUDGET_MILLIS = 1_000.0 / 60.0
