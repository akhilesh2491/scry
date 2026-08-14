@file:Suppress("unused")

package io.github.akhilesh2491.scry.network.ktor

import io.github.akhilesh2491.scry.network.NetworkPlugin
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin

/** Inert mirror of `scry-network-ktor`. */
public class ScryKtorConfig {
    public var networkPlugin: NetworkPlugin? = null
}

/**
 * Installs cleanly and does nothing.
 *
 * Still a real Ktor plugin so `install(ScryKtor)` compiles unchanged — it simply
 * registers no hooks, so requests are not touched at all.
 */
public val ScryKtor: ClientPlugin<ScryKtorConfig> =
    createClientPlugin("ScryKtorNoOp", ::ScryKtorConfig) { }

/** Never thrown here; declared so the type still resolves in a release build. */
public class ScryMockedFailure(message: String) : RuntimeException(message)
