@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.network.okhttp

import io.github.akhilesh2491.scry.network.NetworkPlugin
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Inert mirror of `scry-network-okhttp`.
 *
 * A straight pass-through: no body peeking, no buffering, no allocation beyond
 * the call itself.
 */
public class ScryInterceptor @JvmOverloads constructor(
    networkPlugin: NetworkPlugin? = null,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
