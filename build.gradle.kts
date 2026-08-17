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
// Minor rather than patch: adds public API to scry-ui (ScryScreenBar,
// ScryShareAction, ScryDestructiveAction, ScryCard, ScryStat). See CHANGELOG.md.
version = "0.3.0"

subprojects {
    group = rootProject.group
    version = rootProject.version
}
