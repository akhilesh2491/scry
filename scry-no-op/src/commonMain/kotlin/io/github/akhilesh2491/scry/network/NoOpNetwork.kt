@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.network

import io.github.akhilesh2491.scry.core.ScryEvent
import io.github.akhilesh2491.scry.core.ScryPlugin
import io.github.akhilesh2491.scry.core.ScryScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.jvm.JvmStatic

/** Inert mirrors of `scry-network`. */

public data class HttpHeader(
    public val name: String,
    public val value: String,
)

public data class NetworkTransaction(
    public val id: String,
    public val startedAtMillis: Long,
    public val method: String,
    public val url: String,
    public val durationMillis: Long? = null,
    public val protocol: String? = null,
    public val requestHeaders: List<HttpHeader> = emptyList(),
    public val requestBody: String? = null,
    public val requestBodyTruncated: Boolean = false,
    public val requestBodySize: Long? = null,
    public val responseCode: Int? = null,
    public val responseMessage: String? = null,
    public val responseHeaders: List<HttpHeader> = emptyList(),
    public val responseBody: String? = null,
    public val responseBodyTruncated: Boolean = false,
    public val responseBodySize: Long? = null,
    public val error: String? = null,
) {
    public val host: String get() = ""
    public val path: String get() = ""
    public val isComplete: Boolean get() = false
    public val isSecure: Boolean get() = false
    public val statusLabel: String get() = ""
}

public class NetworkTransactionEvent(
    public val transaction: NetworkTransaction,
    override val timestampMillis: Long,
) : ScryEvent

public class NetworkPluginConfig internal constructor(
    public val maxBodyBytes: Int,
    public val captureRequestBodies: Boolean,
    public val captureResponseBodies: Boolean,
)

public class NetworkPluginBuilder internal constructor() {
    public var maxBodyBytes: Int = 0
    public var captureRequestBodies: Boolean = false
    public var captureResponseBodies: Boolean = false
}

public class NetworkPlugin(
    configure: NetworkPluginBuilder.() -> Unit = {},
) : ScryPlugin {
    public val config: NetworkPluginConfig = NetworkPluginConfig(0, false, false)

    override val id: String = PLUGIN_ID
    override val displayName: String = "Network"
    override val badge: StateFlow<String?> = MutableStateFlow(null)

    public val transactions: StateFlow<List<NetworkTransaction>> = MutableStateFlow(emptyList())

    public val mocks: MockEngine = MockEngine()

    override fun onInstall(scope: ScryScope): Unit = Unit
    override fun onClear(): Unit = Unit

    /** Drops the transaction. Nothing is stored, nothing is displayed. */
    public fun record(transaction: NetworkTransaction): Unit = Unit

    public companion object {
        public const val PLUGIN_ID: String = "scry.network"

        @JvmStatic
        public fun installed(): NetworkPlugin? = null
    }
}
