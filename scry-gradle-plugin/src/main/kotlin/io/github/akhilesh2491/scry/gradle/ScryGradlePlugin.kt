package io.github.akhilesh2491.scry.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import java.util.Properties

/**
 * Wires Scry into debug builds and keeps it out of release builds.
 *
 * ```kotlin
 * plugins { id("io.github.akhilesh2491.scry") }
 *
 * scry {
 *     modules.set(setOf(ScryModule.NETWORK_OKHTTP, ScryModule.PREFS))
 * }
 * ```
 *
 * Replaces this, which every integrator otherwise has to write and maintain by
 * hand for every module they add:
 *
 * ```kotlin
 * debugImplementation("…:scry-core:x")
 * debugImplementation("…:scry-ui:x")
 * debugImplementation("…:scry-network-okhttp:x")
 * releaseImplementation("…:scry-no-op:x")
 * ```
 *
 * Hand-wiring is not just tedious — it is the single most common way an on-device
 * debugger ends up in a shipped app. The verification task below makes that
 * mistake a build failure instead of a security incident.
 */
public class ScryGradlePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions.create(EXTENSION_NAME, ScryExtension::class.java)
        extension.version.convention(pluginVersion())

        target.afterEvaluate {
            if (extension.enabled.get()) {
                addDependencies(target, extension)
            }
            if (extension.failOnReleaseLeak.get()) {
                registerLeakCheck(target)
            }
        }
    }

    /**
     * Adds real modules to debug configurations and the no-op to release.
     *
     * `scry-ui` is always included alongside core: a debugger you cannot open is
     * not a debugger, and forgetting it produces a confusing "nothing happens"
     * rather than a compile error.
     */
    private fun addDependencies(project: Project, extension: ScryExtension) {
        val version = extension.version.get()
        val configurations = project.configurations

        val debugConfiguration = configurations.findByName(DEBUG_IMPLEMENTATION)
        val releaseConfiguration = configurations.findByName(RELEASE_IMPLEMENTATION)

        if (debugConfiguration == null || releaseConfiguration == null) {
            project.logger.warn(
                "[scry] No debugImplementation/releaseImplementation configurations found in " +
                    "${project.path}. Apply the Android application or library plugin before " +
                    "this one, or add the Scry dependencies manually.",
            )
            return
        }

        val debugArtifacts = buildList {
            add("scry-core")
            add("scry-ui")
            extension.modules.get().forEach { add(it.artifact) }
        }.distinct()

        debugArtifacts.forEach { artifact ->
            project.dependencies.add(DEBUG_IMPLEMENTATION, "$GROUP:$artifact:$version")
        }
        project.dependencies.add(RELEASE_IMPLEMENTATION, "$GROUP:$NO_OP_ARTIFACT:$version")
    }

    /**
     * Registers the leak check and makes release assembly depend on it.
     *
     * Offenders are computed through providers so the whole thing survives the
     * configuration cache: `resolvedArtifacts` is a lazy `Provider`, and it is
     * mapped to plain strings before anything reaches the task.
     */
    private fun registerLeakCheck(project: Project) {
        val matched = project.configurations
            .filter { it.isReleaseRuntimeClasspath() && it.isCanBeResolved }
        project.logger.info(
            "[scry] leak check will inspect: " +
                (matched.joinToString { it.name }.ifEmpty { "(no configurations matched)" }),
        )
        val offenderProviders = matched
            .map { configuration ->
                // The resolution graph, not an artifact view: artifact views
                // resolve for a particular artifact type and came back empty for
                // KMP Android projects, silently reporting a clean build. The
                // graph always knows what is on the classpath.
                configuration.incoming.resolutionResult.rootComponent.map { root ->
                    val seen = mutableSetOf<ComponentIdentifier>()
                    val offenders = mutableListOf<String>()
                    fun walk(component: ResolvedComponentResult) {
                        if (!seen.add(component.id)) return
                        offenderNameOrNull(component.id)?.let { offenders += it }
                        component.dependencies
                            .filterIsInstance<ResolvedDependencyResult>()
                            .forEach { walk(it.selected) }
                    }
                    walk(root)
                    offenders.toList()
                }
            }

        val combined = offenderProviders.fold(
            project.provider { emptyList<String>() },
        ) { accumulated, next ->
            accumulated.zip(next) { a, b -> a + b }
        }

        val checkTask = project.tasks.register(LEAK_CHECK_TASK, ScryReleaseLeakCheckTask::class.java) { task ->
            task.group = "verification"
            task.description =
                "Fails if a non-no-op Scry artifact is on a release runtime classpath."
            task.projectPath.set(project.path)
            task.offenders.set(combined)
        }

        project.tasks
            .matching { it.name.startsWith("assembleRelease") || it.name == "bundleRelease" }
            .configureEach { it.dependsOn(checkTask) }
    }

    private fun Configuration.isReleaseRuntimeClasspath(): Boolean =
        name.contains("release", ignoreCase = true) &&
            name.contains("RuntimeClasspath", ignoreCase = true)

    /**
     * Returns the component's display name if it is a real Scry artifact.
     *
     * Matches both published coordinates and project dependencies. Project
     * dependencies matter more than they look: they are how this library's own
     * samples and any composite build consume Scry, so a check that understood
     * only Maven coordinates would pass cleanly on exactly the setups most likely
     * to be misconfigured.
     */
    private fun offenderNameOrNull(owner: ComponentIdentifier): String? {
        val isScry = when (owner) {
            is ModuleComponentIdentifier -> owner.group == GROUP
            is ProjectComponentIdentifier ->
                owner.projectPath.substringAfterLast(':').startsWith("scry-")
            else -> false
        }
        if (!isScry) return null
        if (owner.displayName.contains(NO_OP_ARTIFACT)) return null
        return owner.displayName
    }

    private fun pluginVersion(): String = runCatching {
        ScryGradlePlugin::class.java.classLoader
            .getResourceAsStream(VERSION_RESOURCE)
            .use { stream ->
                Properties().apply { load(stream) }.getProperty("version")
            }
    }.getOrNull() ?: FALLBACK_VERSION

    internal companion object {
        const val EXTENSION_NAME = "scry"
        const val GROUP = "io.github.akhilesh2491.scry"
        const val NO_OP_ARTIFACT = "scry-no-op"
        const val DEBUG_IMPLEMENTATION = "debugImplementation"
        const val RELEASE_IMPLEMENTATION = "releaseImplementation"
        const val LEAK_CHECK_TASK = "checkScryNotInRelease"
        const val VERSION_RESOURCE = "scry-version.properties"
        const val FALLBACK_VERSION = "0.1.0-SNAPSHOT"
    }
}

/** Thrown when a real Scry artifact would ship in a release build. */
public class ScryReleaseLeakException(
    projectPath: String,
    offenders: List<String>,
) : RuntimeException(
    buildString {
        appendLine("Scry is on the release runtime classpath of $projectPath:")
        offenders.forEach { appendLine("  - $it") }
        appendLine()
        appendLine("Scry can read and modify the app's preferences and databases, and it stores")
        appendLine("captured network traffic on disk. It must not ship to users.")
        appendLine()
        appendLine("Fix: depend on Scry through `debugImplementation` only, and use")
        appendLine("`${ScryGradlePlugin.GROUP}:${ScryGradlePlugin.NO_OP_ARTIFACT}` for release —")
        appendLine("or let the `${ScryGradlePlugin.EXTENSION_NAME}` plugin wire it for you.")
        appendLine()
        append("To disable this check: scry { failOnReleaseLeak.set(false) }")
    },
)
