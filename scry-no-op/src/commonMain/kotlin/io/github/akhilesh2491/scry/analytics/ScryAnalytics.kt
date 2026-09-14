@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.analytics

import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Discards every event.
 *
 * The call an app leaves in its analytics wrapper forever, so this is the stub
 * that decides whether that is safe: it holds no reference and allocates nothing.
 */
public object ScryAnalytics {

    @JvmStatic
    public fun detach(): Unit = Unit

    @JvmStatic
    @JvmOverloads
    public fun track(
        name: String,
        params: Map<String, Any?> = emptyMap(),
        destination: String? = null,
    ): Unit = Unit

    @JvmStatic
    public fun screenEntered(screen: String): Unit = Unit

    @JvmStatic
    public fun checkNow(): Unit = Unit
}
