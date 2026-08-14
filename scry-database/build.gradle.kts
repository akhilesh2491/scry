plugins {
    id("scry.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":scry-core"))
            api(project(":scry-ui"))
            // The same driver scry-core uses for its own store, here doing the
            // job it was actually chosen for: opening arbitrary SQLite files.
            api(libs.androidx.sqlite)
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}
