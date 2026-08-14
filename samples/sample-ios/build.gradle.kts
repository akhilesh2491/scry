plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

/**
 * Shared code for the iOS sample, exposed to Xcode as a framework.
 *
 * Not a `scry.library` module: this is an app's shared layer, not a published
 * artifact, so it has no Android target and no explicit-API requirement.
 */
kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        // Debug only. Linking a *release* Compose framework runs whole-program
        // optimisation and needs ~8 GB of Kotlin/Native heap per architecture;
        // `./gradlew build` links every target in parallel, so two of them
        // together exhaust the machine. A sample has no release consumer, and
        // release linking of the library itself is verified separately with
        // `./gradlew :samples:sample-ios:linkReleaseFrameworkIosSimulatorArm64`.
        target.binaries.framework(listOf(org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.DEBUG)) {
            baseName = "SampleShared"
            // Static: simpler to embed, and there is no second consumer to share
            // a dynamic framework with.
            isStatic = true
            // Scry's entry points are called from Swift, so they must be exported
            // rather than hidden inside the framework's implementation.
            export(project(":scry-core"))
            export(project(":scry-ui"))
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":scry-core"))
                api(project(":scry-ui"))
                implementation(project(":scry-network"))
                implementation(project(":scry-network-ktor"))
                implementation(project(":scry-prefs"))
                implementation(project(":scry-database"))
                implementation(project(":scry-crash"))
                implementation(project(":scry-logs"))
                implementation(project(":scry-perf"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}
