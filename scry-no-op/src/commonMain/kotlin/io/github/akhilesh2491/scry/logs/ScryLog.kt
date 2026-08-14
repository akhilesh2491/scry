@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.logs

import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Discards everything.
 *
 * The point of the facade: `ScryLog.i(...)` call sites can stay in shared code
 * that also compiles into release builds, costing nothing there.
 */
public object ScryLog {
    @JvmStatic public fun detach(): Unit = Unit

    @JvmStatic @JvmOverloads
    public fun v(tag: String, message: String, throwable: Throwable? = null): Unit = Unit

    @JvmStatic @JvmOverloads
    public fun d(tag: String, message: String, throwable: Throwable? = null): Unit = Unit

    @JvmStatic @JvmOverloads
    public fun i(tag: String, message: String, throwable: Throwable? = null): Unit = Unit

    @JvmStatic @JvmOverloads
    public fun w(tag: String, message: String, throwable: Throwable? = null): Unit = Unit

    @JvmStatic @JvmOverloads
    public fun e(tag: String, message: String, throwable: Throwable? = null): Unit = Unit

    @JvmStatic @JvmOverloads
    public fun record(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ): Unit = Unit
}
