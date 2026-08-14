plugins {
    id("scry.library")
}

/**
 * API-identical inert replacement for the Scry libraries.
 *
 * Swap it in for release builds:
 * ```kotlin
 * debugImplementation(project(":scry-core"))
 * debugImplementation(project(":scry-network"))
 * debugImplementation(project(":scry-network-okhttp"))
 * releaseImplementation(project(":scry-no-op"))
 * ```
 *
 * Same packages and same signatures as the real modules, so the swap needs no
 * source changes — and *deliberately* conflicting, so having both on one
 * classpath fails loudly rather than silently shipping the real thing.
 *
 * M1 ships one no-op covering core + network + both capture adapters. Per-artifact
 * no-ops and a Gradle plugin that wires the variants automatically are M2.
 *
 * Note on dependencies: core carries none beyond coroutines (`StateFlow` is in the
 * plugin interface). The adapter stubs must keep Ktor and OkHttp, because their
 * public types *are* engine types — but those are libraries the app already ships.
 * What this artifact removes is SQLite, Compose, kotlinx-serialization, and all
 * capture and storage code.
 */
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.ktor.client.core)
            // Interfaces only (a few KB). The multi-megabyte native SQLite build
            // in sqlite-bundled is deliberately excluded.
            api(libs.androidx.sqlite)
        }
        val jvmSharedMain by getting {
            dependencies {
                api(libs.okhttp)
            }
        }
    }
}

/**
 * Fails the build if a real module exposes something this one does not.
 *
 * Without a gate, no-op parity rots on the first public method someone adds and
 * forgets to stub — and the failure surfaces as a broken release build in
 * someone else's project. It has already caught three real gaps, including
 * `REDACTED` landing in the wrong JVM facade class because the no-op file had a
 * different name than the real one.
 */
val realModules = listOf(
    ":scry-core",
    ":scry-network",
    ":scry-prefs",
    ":scry-database",
    ":scry-crash",
    ":scry-logs",
    ":scry-network-ktor",
    ":scry-network-okhttp",
)

val checkNoOpParity by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies scry-no-op mirrors the public API of the real modules."

    val jarTasks = (realModules + ":scry-no-op").map { "$it:desktopJar" }
    dependsOn(jarTasks)

    fun desktopJar(path: String): String {
        val name = path.removePrefix(":")
        return rootProject.layout.projectDirectory
            .file("$name/build/libs/$name-desktop-${rootProject.version}.jar")
            .asFile.absolutePath
    }

    commandLine(
        listOf(
            "python3",
            rootProject.layout.projectDirectory.file("tools/check-noop-parity.py").asFile.absolutePath,
            desktopJar(":scry-no-op"),
        ) + realModules.map(::desktopJar),
    )
}

tasks.named("check") { dependsOn(checkNoOpParity) }
