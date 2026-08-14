import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Shared configuration for every published Scry library module.
 *
 * Lives in a plain Kotlin file rather than being duplicated across the two
 * precompiled script plugins, so "what a Scry module is" is defined once.
 *
 * @param withIos false for modules whose implementation is inherently JVM-only
 *   (the OkHttp adapter). Publishing an empty iOS klib would advertise support
 *   that does not exist.
 */
internal fun Project.configureScryLibrary(withIos: Boolean) {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    fun version(name: String) = libs.findVersion(name).get().requiredVersion

    // Captured here on purpose: inside the `androidLibrary` block, `name` is the
    // *target* name ("android"), not the project's. Using it there silently gave
    // every module the same namespace and moved the generated R class.
    val moduleName = project.name

    extensions.configure<KotlinMultiplatformExtension> {
        // Every public declaration needs explicit visibility and an explicit
        // return type. Non-negotiable for a library whose API surface is the
        // product.
        explicitApi = ExplicitApiMode.Strict

        jvmToolchain(version("jvmToolchain").toInt())

        compilerOptions {
            // `expect`/`actual` classes are still flagged Beta. Scry relies on
            // them for PlatformContext, so the warning is acknowledged rather
            // than worked around.
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }

        (this as ExtensionAware).extensions
            .configure<KotlinMultiplatformAndroidLibraryTarget>("androidLibrary") {
                // ":scry-core" -> "io.github.akhilesh2491.scry.core"
                namespace = "io.github.akhilesh2491." + moduleName.replace('-', '.')
                compileSdk = version("androidCompileSdk").toInt()
                minSdk = version("androidMinSdk").toInt()
                withHostTestBuilder {}.configure { isIncludeAndroidResources = true }
            }

        jvm("desktop")

        if (withIos) {
            // No iosX64: androidx.sqlite does not publish an Intel-simulator
            // variant, and Apple Silicon is the only simulator that matters now.
            iosArm64()
            iosSimulatorArm64()
        }

        sourceSets.apply {
            // Android and desktop are both JVM and share almost all of their
            // platform code. Wired by hand rather than through
            // `applyDefaultHierarchyTemplate`, whose `withAndroidTarget()` does
            // not match the target contributed by AGP 9's KMP plugin — the
            // source set silently ends up in no compilation at all.
            val jvmSharedMain = maybeCreate("jvmSharedMain").apply {
                dependsOn(getByName("commonMain"))
            }
            val jvmSharedTest = maybeCreate("jvmSharedTest").apply {
                dependsOn(getByName("commonTest"))
            }
            listOf("androidMain", "desktopMain").forEach { findByName(it)?.dependsOn(jvmSharedMain) }
            listOf("androidHostTest", "androidUnitTest", "desktopTest")
                .forEach { findByName(it)?.dependsOn(jvmSharedTest) }

            if (withIos) {
                // Same reasoning: the default hierarchy is not in play, so the
                // shared iOS source set is created and wired explicitly.
                val iosMain = maybeCreate("iosMain").apply {
                    dependsOn(getByName("commonMain"))
                }
                val iosTest = maybeCreate("iosTest").apply {
                    dependsOn(getByName("commonTest"))
                }
                listOf("iosArm64Main", "iosSimulatorArm64Main")
                    .forEach { findByName(it)?.dependsOn(iosMain) }
                listOf("iosArm64Test", "iosSimulatorArm64Test")
                    .forEach { findByName(it)?.dependsOn(iosTest) }
            }

            commonTest.dependencies {
                implementation(libs.findLibrary("kotlin-test").get())
                implementation(libs.findLibrary("kotlinx-coroutines-test").get())
            }

            // Android's `sqlite-bundled` ships an Android .so, which a host-side
            // JVM unit test cannot load — any common test touching ScryStore dies
            // with UnsatisfiedLinkError on `testAndroidHostTest`. The JVM variant
            // supplies the same SQLite build as a host native library. Test-only.
            findByName("androidHostTest")?.dependencies {
                implementation(libs.findLibrary("androidx-sqlite-bundled-jvm").get())
            }
        }
    }
}
