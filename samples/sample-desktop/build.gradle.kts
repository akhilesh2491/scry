plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    implementation(project(":scry-ui"))
    implementation(project(":scry-network-ktor"))
    implementation(compose.desktop.currentOs)
    implementation(project(":scry-network-okhttp"))
    implementation(libs.ktor.client.cio)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "io.github.akhilesh2491.scry.sample.MainKt"
    }
}
