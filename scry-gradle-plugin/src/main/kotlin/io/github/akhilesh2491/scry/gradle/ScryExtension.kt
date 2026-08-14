package io.github.akhilesh2491.scry.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

/** Which Scry plugin each artifact corresponds to. */
public enum class ScryModule(internal val artifact: String) {
    NETWORK_KTOR("scry-network-ktor"),
    NETWORK_OKHTTP("scry-network-okhttp"),
    PREFS("scry-prefs"),
    DATABASE("scry-database"),
    CRASH("scry-crash"),
}

/**
 * Configuration for the `io.github.akhilesh2491.scry` plugin.
 *
 * ```kotlin
 * scry {
 *     modules.set(setOf(ScryModule.NETWORK_OKHTTP, ScryModule.PREFS, ScryModule.CRASH))
 * }
 * ```
 */
public abstract class ScryExtension @Inject constructor(objects: ObjectFactory) {

    /**
     * Which Scry plugin artifacts to add.
     *
     * Defaults to the network (OkHttp) plugin only, because that is the reason
     * most people install Scry and because pulling in the storage inspectors
     * silently would be a surprise.
     */
    public val modules: SetProperty<ScryModule> =
        objects.setProperty(ScryModule::class.java).convention(setOf(ScryModule.NETWORK_OKHTTP))

    /**
     * Scry version to use. Defaults to the version of this plugin, so the plugin
     * and the libraries can never drift apart.
     */
    public val version: Property<String> = objects.property(String::class.java)

    /** Set false to add no dependencies at all — useful for a staged rollout. */
    public val enabled: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)

    /**
     * Fail the build if a real Scry artifact reaches a release runtime classpath.
     *
     * On by default. This is the whole reason the plugin exists: the manual
     * `debugImplementation`/`releaseImplementation` swap is one careless edit away
     * from shipping a tool that exposes the user's preferences and database to
     * anyone holding the phone.
     */
    public val failOnReleaseLeak: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)
}
