plugins {
    // No `kotlin.android` plugin: AGP 9 has built-in Kotlin support and
    // rejects it. The Compose compiler plugin is still applied explicitly.
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    id("io.github.akhilesh2491.scry")
}

// The sample consumes Scry as project dependencies (below) rather than published
// coordinates, so the plugin only contributes its release-leak check here.
scry {
    enabled.set(false)
}

android {
    namespace = "io.github.akhilesh2491.scry.sample.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.akhilesh2491.scry.sample.android"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        val java = JavaVersion.toVersion(libs.versions.jvmToolchain.get())
        sourceCompatibility = java
        targetCompatibility = java
    }
}

dependencies {
    // Debug gets the real thing; release gets the API-identical no-op. The sample
    // wires this by hand to demonstrate what the Gradle plugin automates — and
    // because `assembleRelease` compiling against the no-op is the strongest
    // possible check that the two really do have the same public API.
    debugImplementation(project(":scry-ui"))
    debugImplementation(project(":scry-network"))
    debugImplementation(project(":scry-prefs"))
    debugImplementation(project(":scry-database"))
    debugImplementation(project(":scry-crash"))
    debugImplementation(project(":scry-logs"))
    debugImplementation(project(":scry-perf"))
    debugImplementation(project(":scry-network-ktor"))
    debugImplementation(project(":scry-network-okhttp"))
    releaseImplementation(project(":scry-no-op"))

    // The app brings its own Compose. Relying on Scry to supply it transitively
    // would mean the release build (which only has the no-op) loses Compose —
    // exactly the mistake this sample should not model.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.okhttp)
}
