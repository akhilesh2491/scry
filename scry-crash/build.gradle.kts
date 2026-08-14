plugins {
    id("scry.compose-library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":scry-core"))
            api(project(":scry-ui"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
