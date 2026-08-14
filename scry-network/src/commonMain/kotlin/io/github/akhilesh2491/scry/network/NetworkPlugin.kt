package io.github.akhilesh2491.scry.network

import androidx.compose.runtime.Composable
import io.github.akhilesh2491.scry.core.Redactor
import io.github.akhilesh2491.scry.core.Scry
import io.github.akhilesh2491.scry.core.ScryEvent
import io.github.akhilesh2491.scry.core.ScryScope
import io.github.akhilesh2491.scry.core.ScryContextSection
import io.github.akhilesh2491.scry.core.ScryStore
import io.github.akhilesh2491.scry.ui.ScryUiPlugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmStatic

/** Published when a transaction completes, so other plugins can correlate. */
public class NetworkTransactionEvent(
    public val transaction: NetworkTransaction,
    override val timestampMillis: Long,
) : ScryEvent

/** Tuning for [NetworkPlugin]. */
public class NetworkPluginConfig internal constructor(
    public val maxBodyBytes: Int,
    public val captureRequestBodies: Boolean,
    public val captureResponseBodies: Boolean,
)

/** Kotlin DSL / Java builder for [NetworkPluginConfig]. */
public class NetworkPluginBuilder internal constructor() {
    /**
     * Bodies larger than this are truncated with a marker.
     *
     * Capping matters more than it looks: an un-capped inspector turns a 40 MB
     * file download into a 40 MB string in memory and then a 40 MB row on disk.
     */
    public var maxBodyBytes: Int = 256 * 1024
    public var captureRequestBodies: Boolean = true
    public var captureResponseBodies: Boolean = true

    internal fun build(): NetworkPluginConfig = NetworkPluginConfig(
        maxBodyBytes = maxBodyBytes,
        captureRequestBodies = captureRequestBodies,
        captureResponseBodies = captureResponseBodies,
    )
}

/**
 * Captures and displays HTTP traffic.
 *
 * The plugin owns storage and presentation; per-engine adapters
 * (`scry-network-ktor`, `scry-network-okhttp`) only translate their engine's
 * types into [NetworkTransaction] and hand them here.
 */
public class NetworkPlugin(
    configure: NetworkPluginBuilder.() -> Unit = {},
) : ScryUiPlugin {

    public val config: NetworkPluginConfig = NetworkPluginBuilder().apply(configure).build()

    override val id: String = PLUGIN_ID
    override val displayName: String = "Network"

    private val _transactions = MutableStateFlow<List<NetworkTransaction>>(emptyList())

    /** Captured transactions, newest first. */
    public val transactions: StateFlow<List<NetworkTransaction>> = _transactions.asStateFlow()

    private val _badge = MutableStateFlow<String?>(null)
    override val badge: StateFlow<String?> = _badge.asStateFlow()

    /**
     * Request mocking, throttling and offline simulation.
     *
     * Lives on the plugin rather than in an adapter so one rule set applies to
     * every capture surface — a rule written while using Ktor still fires for a
     * Retrofit call in the same app.
     */
    public val mocks: MockEngine = MockEngine()

    private var store: ScryStore? = null
    private var events: io.github.akhilesh2491.scry.core.ScryEventBus? = null
    private var redactor: Redactor = Redactor.DEFAULT

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun onInstall(scope: ScryScope) {
        store = scope.store
        events = scope.events
        redactor = scope.config.redactor

        // Restore anything that survived the last process death. Being able to
        // inspect the call that preceded a crash is most of the point.
        val restored = scope.store.read(PLUGIN_ID).mapNotNull { record ->
            runCatching { json.decodeFromString<NetworkTransaction>(record.payload) }.getOrNull()
        }
        _transactions.value = restored
        updateBadge()

        // Contribute recent traffic to crash and bug reports. `scry-crash` never
        // learns this module exists — the registry in core is the only contact
        // point, which is what keeps the plugins independently droppable.
        scope.contextRegistry.register(PLUGIN_ID) {
            val recent = _transactions.value.take(CONTEXT_TRANSACTION_COUNT)
            if (recent.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    ScryContextSection(
                        title = "Last ${recent.size} network calls",
                        body = recent.joinToString("\n") { transaction ->
                            buildString {
                                append(transaction.statusLabel.padEnd(6))
                                append(transaction.method.padEnd(6))
                                append(transaction.url)
                                transaction.durationMillis?.let { append("  (${it}ms)") }
                                transaction.error?.let { append("  error=$it") }
                            }
                        },
                    ),
                )
            }
        }
    }

    override fun onClear() {
        _transactions.value = emptyList()
        store?.clear(PLUGIN_ID)
        updateBadge()
    }

    /**
     * Records a transaction, or updates an existing one with the same id.
     *
     * Adapters call this twice per exchange — once when the request goes out and
     * once when the response arrives — so an in-flight call is visible in the UI
     * rather than appearing only after it finishes. That is what makes a hung
     * request diagnosable.
     */
    public fun record(transaction: NetworkTransaction) {
        val redacted = transaction.redactWith(redactor)

        _transactions.update { existing ->
            val index = existing.indexOfFirst { it.id == redacted.id }
            if (index >= 0) {
                existing.toMutableList().also { it[index] = redacted }
            } else {
                listOf(redacted) + existing
            }
        }

        store?.put(
            pluginId = PLUGIN_ID,
            id = redacted.id,
            payload = json.encodeToString(redacted),
            createdAtMillis = redacted.startedAtMillis,
        )
        updateBadge()

        // Let other plugins react to completed calls.
        if (redacted.isComplete) {
            events?.publish(NetworkTransactionEvent(redacted, redacted.startedAtMillis))
        }
    }

    private fun updateBadge() {
        val count = _transactions.value.size
        _badge.value = if (count == 0) null else count.toString()
    }

    @Composable
    override fun Content() {
        NetworkScreen(plugin = this)
    }

    public companion object {
        public const val PLUGIN_ID: String = "scry.network"

        /** How many recent calls to attach to a crash report. */
        private const val CONTEXT_TRANSACTION_COUNT: Int = 25

        /**
         * The installed [NetworkPlugin], if there is one.
         *
         * Capture adapters use this so `ScryKtor()` works with no wiring. Passing
         * the instance explicitly is still supported and is what tests should do.
         */
        @JvmStatic
        public fun installed(): NetworkPlugin? =
            Scry.instance?.plugins?.firstOrNull { it is NetworkPlugin } as? NetworkPlugin
    }
}

/** Applies header and body redaction before anything is stored or displayed. */
internal fun NetworkTransaction.redactWith(redactor: Redactor): NetworkTransaction = copy(
    requestHeaders = requestHeaders.map { HttpHeader(it.name, redactor.redactHeaderValue(it.name, it.value)) },
    responseHeaders = responseHeaders.map { HttpHeader(it.name, redactor.redactHeaderValue(it.name, it.value)) },
    requestBody = requestBody?.let(redactor::redactJsonBody),
    responseBody = responseBody?.let(redactor::redactJsonBody),
)

private inline fun <T> MutableStateFlow<T>.update(block: (T) -> T) {
    value = block(value)
}
