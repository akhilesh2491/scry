package io.github.akhilesh2491.scry.logs

import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * The logging facade.
 *
 * ```kotlin
 * ScryLog.i("Checkout", "Placing order $id")
 * ScryLog.e("Checkout", "Payment failed", throwable)
 * ```
 *
 * Calls are no-ops until a [LogPlugin] is installed, and cost a null check when
 * it is not — so leaving them in shared code that also runs in release builds
 * (against `scry-no-op`) is safe.
 *
 * This does **not** replace your logger. It is a sink Scry can display; route
 * your existing logger into it with [record] if you already have one, rather
 * than rewriting call sites.
 */
public object ScryLog {

    internal var plugin: LogPlugin? = null
        private set

    internal fun attach(plugin: LogPlugin) {
        this.plugin = plugin
    }

    /** Detaches the sink. Used when Scry is uninstalled, and by tests. */
    @JvmStatic
    public fun detach() {
        plugin = null
    }

    @JvmStatic
    @JvmOverloads
    public fun v(tag: String, message: String, throwable: Throwable? = null): Unit =
        record(LogLevel.VERBOSE, tag, message, throwable)

    @JvmStatic
    @JvmOverloads
    public fun d(tag: String, message: String, throwable: Throwable? = null): Unit =
        record(LogLevel.DEBUG, tag, message, throwable)

    @JvmStatic
    @JvmOverloads
    public fun i(tag: String, message: String, throwable: Throwable? = null): Unit =
        record(LogLevel.INFO, tag, message, throwable)

    @JvmStatic
    @JvmOverloads
    public fun w(tag: String, message: String, throwable: Throwable? = null): Unit =
        record(LogLevel.WARN, tag, message, throwable)

    @JvmStatic
    @JvmOverloads
    public fun e(tag: String, message: String, throwable: Throwable? = null): Unit =
        record(LogLevel.ERROR, tag, message, throwable)

    /**
     * Records at an explicit level.
     *
     * The bridge for an existing logger: point your Timber tree, SLF4J appender
     * or custom facade at this and every call site keeps working unchanged.
     */
    @JvmStatic
    @JvmOverloads
    public fun record(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        plugin?.record(level, tag, message, throwable)
    }
}
