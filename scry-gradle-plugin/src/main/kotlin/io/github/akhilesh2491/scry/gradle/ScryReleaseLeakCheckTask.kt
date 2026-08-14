package io.github.akhilesh2491.scry.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Fails when a real Scry artifact is on a release runtime classpath.
 *
 * A real task class with declared inputs rather than a `doLast` closure: the
 * closure captured `Project`, which is not serializable and breaks the
 * configuration cache. Component identifiers are resolved into plain strings at
 * configuration time so nothing unserializable crosses into execution.
 */
@DisableCachingByDefault(
    because = "Produces no output to cache. It compares already-resolved strings, so a cache " +
        "lookup would cost more than the check, and a cache hit would skip the one thing " +
        "this task exists to guarantee runs on every release build.",
)
public abstract class ScryReleaseLeakCheckTask : DefaultTask() {

    @get:Input
    public abstract val projectPath: Property<String>

    /** Display names of offending components; empty means the build is clean. */
    @get:Input
    public abstract val offenders: ListProperty<String>

    @TaskAction
    public fun check() {
        val found = offenders.get().distinct().sorted()
        if (found.isNotEmpty()) {
            throw ScryReleaseLeakException(projectPath.get(), found)
        }
        logger.info("[scry] No Scry artifacts on the release runtime classpath.")
    }
}
