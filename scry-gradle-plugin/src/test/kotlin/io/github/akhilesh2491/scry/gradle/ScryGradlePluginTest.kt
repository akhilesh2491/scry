package io.github.akhilesh2491.scry.gradle

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScryGradlePluginTest {

    /**
     * Builds a project with the Android-style configurations the plugin looks for.
     *
     * Standing up real AGP in a unit test is slow and brittle; the plugin only
     * cares that `debugImplementation` and `releaseImplementation` exist, so the
     * test creates them directly.
     */
    private fun project(withAndroidConfigurations: Boolean = true): Project =
        ProjectBuilder.builder().build().also { project ->
            if (withAndroidConfigurations) {
                project.configurations.create("debugImplementation")
                project.configurations.create("releaseImplementation")
            }
            project.pluginManager.apply(ScryGradlePlugin::class.java)
        }

    private fun Project.evaluate() = (this as ProjectInternal).evaluate()

    private fun Project.dependencyNotations(configuration: String): List<String> =
        configurations.getByName(configuration).dependencies.map {
            "${it.group}:${it.name}:${it.version}"
        }

    @Test
    fun `registers the extension`() {
        assertNotNull(project().extensions.findByName("scry"))
    }

    @Test
    fun `adds core and ui to debug by default`() {
        val project = project()
        project.evaluate()

        val debug = project.dependencyNotations("debugImplementation")
        assertTrue(debug.any { it.contains("scry-core") }, debug.toString())
        assertTrue(debug.any { it.contains("scry-ui") }, "the UI must come along; $debug")
    }

    @Test
    fun `defaults to the okhttp network module only`() {
        val project = project()
        project.evaluate()

        val debug = project.dependencyNotations("debugImplementation")
        assertTrue(debug.any { it.contains("scry-network-okhttp") })
        assertFalse(
            debug.any { it.contains("scry-prefs") || it.contains("scry-database") },
            "storage inspectors must be opt-in, not silent; $debug",
        )
    }

    @Test
    fun `adds the requested modules`() {
        val project = project()
        project.extensions.getByType(ScryExtension::class.java).modules.set(
            setOf(ScryModule.PREFS, ScryModule.DATABASE, ScryModule.CRASH, ScryModule.PERF),
        )
        project.evaluate()

        val debug = project.dependencyNotations("debugImplementation")
        assertTrue(debug.any { it.contains("scry-prefs") })
        assertTrue(debug.any { it.contains("scry-database") })
        assertTrue(debug.any { it.contains("scry-crash") })
        assertTrue(debug.any { it.contains("scry-perf") })
    }

    @Test
    fun `the performance plugin is opt-in`() {
        val project = project()
        project.evaluate()

        // It registers a frame callback for the life of the process; adding that
        // to every project by default would be a surprise.
        assertFalse(
            project.dependencyNotations("debugImplementation").any { it.contains("scry-perf") },
        )
    }

    @Test
    fun `release gets only the no-op`() {
        val project = project()
        project.extensions.getByType(ScryExtension::class.java).modules.set(
            setOf(ScryModule.PREFS, ScryModule.DATABASE),
        )
        project.evaluate()

        val release = project.dependencyNotations("releaseImplementation")
        assertEquals(1, release.size, release.toString())
        assertTrue(release.single().contains("scry-no-op"))
    }

    @Test
    fun `adds nothing when disabled`() {
        val project = project()
        project.extensions.getByType(ScryExtension::class.java).enabled.set(false)
        project.evaluate()

        assertTrue(project.dependencyNotations("debugImplementation").isEmpty())
        assertTrue(project.dependencyNotations("releaseImplementation").isEmpty())
    }

    @Test
    fun `registers the leak check task by default`() {
        val project = project()
        project.evaluate()
        assertNotNull(project.tasks.findByName("checkScryNotInRelease"))
    }

    @Test
    fun `leak check can be turned off`() {
        val project = project()
        project.extensions.getByType(ScryExtension::class.java).failOnReleaseLeak.set(false)
        project.evaluate()
        assertNull(project.tasks.findByName("checkScryNotInRelease"))
    }

    @Test
    fun `warns instead of failing when android configurations are absent`() {
        // Applying the plugin to a plain JVM module should not break the build;
        // a debugging library must never be the reason someone's build stops.
        val project = project(withAndroidConfigurations = false)
        project.evaluate()
        assertNull(project.configurations.findByName("debugImplementation"))
    }

    @Test
    fun `version defaults to the plugin's own version`() {
        val project = project()
        val version = project.extensions.getByType(ScryExtension::class.java).version.get()
        assertTrue(version.isNotBlank())
    }

    @Test
    fun `explicit version overrides the default`() {
        val project = project()
        project.extensions.getByType(ScryExtension::class.java).version.set("9.9.9")
        project.evaluate()

        assertTrue(
            project.dependencyNotations("debugImplementation").all { it.endsWith(":9.9.9") },
            project.dependencyNotations("debugImplementation").toString(),
        )
    }

    @Test
    fun `leak exception explains the risk and the fix`() {
        val message = ScryReleaseLeakException(
            ":app",
            listOf("io.github.akhilesh2491.scry:scry-prefs:0.1.0"),
        ).message.orEmpty()

        assertTrue(message.contains("scry-prefs"))
        assertTrue(message.contains("must not ship"), message)
        assertTrue(message.contains("debugImplementation"), message)
    }
}
