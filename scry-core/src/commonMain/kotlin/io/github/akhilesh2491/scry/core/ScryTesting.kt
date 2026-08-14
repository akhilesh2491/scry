package io.github.akhilesh2491.scry.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Helpers for testing plugins without installing Scry.
 *
 * Shipped in the main artifact rather than a test-only one on purpose: a plugin
 * author outside this repo needs to build a [ScryScope] to unit-test against,
 * and if the only way to get one is `Scry.install()` then every plugin test needs
 * a platform context and a real database file. That friction is how plugin
 * ecosystems end up untested.
 */
public object ScryTesting {

    /**
     * A [ScryScope] backed by an in-memory store.
     *
     * [context] is optional because most plugins never touch it; accessing it
     * when none was supplied fails loudly rather than handing back a fake.
     */
    @JvmStatic
    @JvmOverloads
    public fun scope(
        store: ScryStore = ScryStore.inMemory(),
        redactor: Redactor = Redactor.DEFAULT,
        retention: Retention = Retention.DEFAULT,
        context: PlatformContext? = null,
    ): ScryScope = TestScryScope(
        store = store,
        config = ScryConfig(
            retention = retention,
            plugins = emptyList(),
            redactor = redactor,
            allowInReleaseBuilds = true,
        ),
        providedContext = context,
    )
}

private class TestScryScope(
    override val store: ScryStore,
    override val config: ScryConfig,
    private val providedContext: PlatformContext?,
) : ScryScope {
    override val context: PlatformContext
        get() = providedContext ?: error(
            "No PlatformContext was supplied to ScryTesting.scope(). " +
                "Pass one if the plugin under test needs it.",
        )
    override val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob())
    override val events: ScryEventBus = ScryEventBus()
    override val contextRegistry: ScryContextRegistry = ScryContextRegistry()
}
