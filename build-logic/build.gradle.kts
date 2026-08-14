plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    // Put the Gradle plugins on the build-logic classpath so the precompiled
    // script plugins below can apply them by id.
    implementation(libs.plugin.kotlin.gradle)
    implementation(libs.plugin.android.gradle)
    implementation(libs.plugin.compose.compiler)
    implementation(libs.plugin.compose.multiplatform)
    implementation(libs.plugin.maven.publish)
}
