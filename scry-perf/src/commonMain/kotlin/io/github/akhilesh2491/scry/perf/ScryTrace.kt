package io.github.akhilesh2491.scry.perf

import kotlin.jvm.JvmStatic

/**
 * An open span. Close it to record the measurement.
 *
 * `AutoCloseable` so Kotlin callers can `use { }` it and Java callers can put it
 * in try-with-resources, but [ScryTrace.measure] is the shorter path for both.
 */
public class ScrySpan internal constructor(
    public val name: String,
    internal val startedMonotonicMillis: Long,
    internal val startedAtMillis: Long,
    internal val depth: Int,
) : AutoCloseable {

    private var closed: Boolean = false

    /** Records the span. Closing twice is a no-op, not an error. */
    override fun close() {
        if (closed) return
        closed = true
        ScryTrace.finish(this)
    }
}

/**
 * The manual timing facade.
 *
 * ```kotlin
 * val order = ScryTrace.measure("checkout") { placeOrder(cart) }
 * ```
 *
 * Calls cost a null check until a [PerfPlugin] is installed, so leaving them in
 * shared code that also compiles against `scry-no-op` in release is safe.
 *
 * Use it for the work the automatic instrumentation cannot see: a database
 * migration, an image decode, the gap between a tap and a result.
 */
public object ScryTrace {

    internal var plugin: PerfPlugin? = null
        private set

    /**
     * How many spans are currently open.
     *
     * Deliberately a plain counter and not a per-thread stack: it exists to
     * indent nested spans in the UI, and a debugging tool that adds a
     * thread-local lookup to every timed block is measuring itself. Concurrent
     * spans on different threads will report an approximate depth.
     */
    private var openSpans: Int = 0

    internal fun attach(plugin: PerfPlugin) {
        this.plugin = plugin
    }

    /** Detaches the sink. Used when Scry is uninstalled, and by tests. */
    @JvmStatic
    public fun detach() {
        plugin = null
        openSpans = 0
    }

    /** Opens a span. The caller must [ScrySpan.close] it. */
    @JvmStatic
    public fun begin(name: String): ScrySpan {
        val span = ScrySpan(
            name = name,
            startedMonotonicMillis = monotonicMillis(),
            startedAtMillis = nowMillis(),
            depth = openSpans,
        )
        openSpans++
        return span
    }

    /** Closes a span opened by [begin]. Equivalent to `span.close()`. */
    @JvmStatic
    public fun end(span: ScrySpan) {
        span.close()
    }

    internal fun finish(span: ScrySpan) {
        if (openSpans > 0) openSpans--
        plugin?.recordSpan(
            name = span.name,
            durationMillis = monotonicMillis() - span.startedMonotonicMillis,
            startedAtMillis = span.startedAtMillis,
            depth = span.depth,
        )
    }

    /** Times [block] and returns its result. The span is recorded even if it throws. */
    public inline fun <T> measure(name: String, block: () -> T): T {
        val span = begin(name)
        try {
            return block()
        } finally {
            span.close()
        }
    }

    /**
     * Announces that [screen] is being shown.
     *
     * Deliberately not a `@Composable`: everything an app calls in shared code
     * must have an inert mirror in `scry-no-op`, and a `@Composable` mirror would
     * mean shipping Compose in release builds — the one thing that artifact
     * exists to prevent. A plain call works from a composable, an `Activity`, a
     * `Fragment` and a UIKit view controller alike.
     *
     * ```kotlin
     * LaunchedEffect(Unit) { ScryTrace.screenEntered("Checkout") }
     * ```
     *
     * Time-to-initial-display is completed by the next frame Scry observes, so
     * on desktop — which has no frame monitor — use [screenDisplayed] instead.
     */
    @JvmStatic
    public fun screenEntered(screen: String) {
        plugin?.screenEntered(screen)
    }

    /**
     * Records a screen load the caller measured itself.
     *
     * For hosts Scry cannot instrument automatically, and for desktop.
     */
    @JvmStatic
    public fun screenDisplayed(screen: String, ttidMillis: Long) {
        plugin?.recordScreenLoad(screen, ScreenKind.COMPOSABLE, ttidMillis)
    }

    /**
     * Reports that [screen] now shows real content, [ttfdMillis] after it started
     * loading.
     *
     * The manual counterpart to `Activity.reportFullyDrawn()`. There is no
     * cross-platform way to detect this — only the app knows when its data has
     * arrived — so it is opt-in on every target rather than automatic on one.
     */
    @JvmStatic
    public fun reportFullyDrawn(screen: String, ttfdMillis: Long) {
        plugin?.recordFullyDrawn(screen, ttfdMillis)
    }
}
