plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
}

group = "io.github.akhilesh2491.scry"
// Minor rather than patch: adds a new published module (scry-analytics) and
// public API for the launcher surfaces (launchers { }, ScryBubble and friends).
// See CHANGELOG.md.
version = "0.4.0"

subprojects {
    group = rootProject.group
    version = rootProject.version
}
