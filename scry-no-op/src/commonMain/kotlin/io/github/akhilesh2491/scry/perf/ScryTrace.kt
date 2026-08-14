@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.perf

import kotlin.jvm.JvmStatic

public class ScrySpan internal constructor(
    public val name: String,
    startedMonotonicMillis: Long,
    startedAtMillis: Long,
    depth: Int,
) : AutoCloseable {
    override fun close(): Unit = Unit
}

/**
 * Times nothing.
 *
 * The reason `ScryTrace` calls are safe to leave in shared code: in release this
 * is what they compile against, and `measure` costs one object allocation that
 * escape analysis removes.
 */
public object ScryTrace {

    @JvmStatic
    public fun detach(): Unit = Unit

    @JvmStatic
    public fun begin(name: String): ScrySpan = ScrySpan(name, 0, 0, 0)

    @JvmStatic
    public fun end(span: ScrySpan): Unit = Unit

    public inline fun <T> measure(name: String, block: () -> T): T = block()

    @JvmStatic
    public fun screenEntered(screen: String): Unit = Unit

    @JvmStatic
    public fun screenDisplayed(screen: String, ttidMillis: Long): Unit = Unit

    @JvmStatic
    public fun reportFullyDrawn(screen: String, ttfdMillis: Long): Unit = Unit
}
