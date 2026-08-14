package io.github.akhilesh2491.scry.perf

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import io.github.akhilesh2491.scry.core.PlatformContext

/**
 * Android instrumentation: activity lifecycle, first draw, and frame timing.
 *
 * Everything here runs on the main thread, including the frame callback. That is
 * a deliberate trade: `FrameMetrics` can be delivered to a background handler,
 * but then screen changes and frame samples race on the plugin's state, and a
 * debugging tool that occasionally reports a frame against the wrong screen is
 * worse than one that costs a message dispatch per frame.
 */
internal actual fun startPerfMonitor(context: PlatformContext, plugin: PerfPlugin): AutoCloseable? {
    val application = context.androidContext as? Application ?: return null
    return AndroidPerfMonitor(application, plugin).also { it.start() }
}

private class AndroidPerfMonitor(
    private val application: Application,
    private val plugin: PerfPlugin,
) : Application.ActivityLifecycleCallbacks, AutoCloseable {

    private val handler = Handler(Looper.getMainLooper())

    private val startup = StartupTracker(
        processStartMillis = processStartMillis(),
        installMillis = monotonicMillis(),
    )

    /** Frame listeners, one per window, removed as activities are destroyed. */
    private val frameListeners = mutableMapOf<Activity, Window.OnFrameMetricsAvailableListener>()

    private val fragments = if (plugin.config.trackFragments) FragmentTracking(plugin) else null

    fun start() {
        // No Choreographer fallback below API 24. Deriving frame durations from
        // `postFrameCallback` deltas requires re-posting a callback every frame,
        // which keeps the Choreographer awake and so produces the frames it
        // claims to be observing — the same reason desktop captures nothing. API
        // 23 keeps startup, screen loads and spans, and the screen says why.
        if (plugin.config.trackFrames && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            plugin.markFramesUnsupported(
                "Frame timing needs Android 7.0 (API 24) for FrameMetrics; " +
                    "this device is API ${Build.VERSION.SDK_INT}.",
            )
        }
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun close() {
        application.unregisterActivityLifecycleCallbacks(this)
        frameListeners.keys.toList().forEach(::stopFrameCapture)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.isScryOwn()) return

        if (plugin.config.trackStartup) {
            startup.onActivityCreated(
                hasSavedState = savedInstanceState != null,
                nowMillis = monotonicMillis(),
            )
        }
        fragments?.attach(activity)

        val enteredAt = monotonicMillis()
        activity.onNextDraw { drawnAt ->
            if (plugin.config.trackStartup) {
                startup.onFirstFrame(drawnAt)?.let(plugin::recordStartup)
            }
            if (plugin.config.trackScreens) {
                plugin.recordScreenLoad(
                    screen = activity.screenName(),
                    kind = ScreenKind.ACTIVITY,
                    ttidMillis = drawnAt - enteredAt,
                )
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity.isScryOwn()) return
        if (!plugin.config.trackStartup) return

        // A hot start creates nothing, so its first draw has to be watched on the
        // activity that was already there.
        if (startup.onActivityStarted(monotonicMillis())) {
            activity.onNextDraw { drawnAt ->
                startup.onFirstFrame(drawnAt)?.let(plugin::recordStartup)
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity.isScryOwn()) return

        // Attribution only: this activity's own load was measured from its first
        // draw, and starting a second clock here would record it twice.
        plugin.attributeFramesTo(activity.screenName())
        if (plugin.config.trackFrames) startFrameCapture(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity.isScryOwn()) return
        stopFrameCapture(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity.isScryOwn()) return
        if (plugin.config.trackStartup) startup.onActivityStopped()
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        stopFrameCapture(activity)
        fragments?.detach(activity)
    }

    // ---- frame capture ---------------------------------------------------

    private fun startFrameCapture(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (frameListeners.containsKey(activity)) return

        val budgetMillis = activity.frameBudgetMillis()
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val totalMillis = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / NANOS_PER_MILLI
            plugin.recordFrame(plugin.currentScreen, totalMillis, budgetMillis)
        }

        // A window can be torn down between resume and this call on some OEM
        // builds; a debug tool must not be the thing that crashes the app.
        runCatching { activity.window.addOnFrameMetricsAvailableListener(listener, handler) }
            .onSuccess { frameListeners[activity] = listener }
    }

    private fun stopFrameCapture(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val listener = frameListeners.remove(activity) ?: return
        runCatching { activity.window.removeOnFrameMetricsAvailableListener(listener) }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}

/**
 * Runs [onDrawn] once, after the window's first draw has completed.
 *
 * `onDraw` fires *before* the frame is finished, so the timestamp is taken in a
 * posted runnable instead: by the time the main thread gets back to its queue,
 * the traversal that drew the frame is over. This is also why the listener is
 * removed from the post rather than from `onDraw` — removing a draw listener
 * while the tree is drawing throws.
 */
private fun Activity.onNextDraw(onDrawn: (drawnAtMillis: Long) -> Unit) {
    (window?.decorView ?: return).onNextDraw(onDrawn)
}

internal fun View.onNextDraw(onDrawn: (drawnAtMillis: Long) -> Unit) {
    val decorView: View = this

    val observer = decorView.viewTreeObserver
    if (!observer.isAlive) return

    observer.addOnDrawListener(
        object : ViewTreeObserver.OnDrawListener {
            private var fired = false

            override fun onDraw() {
                if (fired) return
                fired = true
                decorView.post {
                    runCatching { decorView.viewTreeObserver.removeOnDrawListener(this) }
                    onDrawn(SystemClock.uptimeMillis())
                }
            }
        },
    )
}

/** Frame budget from the display's real refresh rate, not a hardcoded 60 Hz. */
private fun Activity.frameBudgetMillis(): Double {
    val refreshRate = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.refreshRate
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.refreshRate
        }
    }.getOrNull()

    val hz = refreshRate?.takeIf { it > 1f } ?: DEFAULT_REFRESH_HZ
    return 1000.0 / hz
}

private fun Activity.screenName(): String = this::class.simpleName ?: "Activity"

/**
 * Scry's own UI is not the app's performance.
 *
 * Without this, opening the plugin list attributes Scry's frames to the app and
 * every session ends with a janky screen called `ScryActivity`.
 *
 * Matched by exact class name, not by package prefix: Scry's own sample app lives
 * under `io.github.akhilesh2491.scry.sample`, and a prefix test silently ignored
 * every activity in it — which is to say the tool measured nothing at all and
 * looked like it was working. Any consumer who nests their app under a matching
 * package would have hit the same thing.
 */
private fun Activity.isScryOwn(): Boolean = this::class.qualifiedName == SCRY_ACTIVITY_CLASS

/** Kept as a string for the same reason `enableScryUi` does: `scry-ui` may be absent. */
private const val SCRY_ACTIVITY_CLASS = "io.github.akhilesh2491.scry.ui.ScryActivity"

private const val DEFAULT_REFRESH_HZ = 60f
