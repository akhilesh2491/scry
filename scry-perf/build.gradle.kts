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

        androidMain.dependencies {
            // compileOnly, never published: apps that use Fragments get fragment
            // load times automatically, and apps that do not pay nothing — not a
            // transitive dependency, not a method count, not a line in their
            // dependency report. Guarded at runtime by a classpath check.
            compileOnly(libs.androidx.fragment)
        }
    }
}
