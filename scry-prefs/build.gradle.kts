plugins {
    id("scry.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":scry-core"))
            api(project(":scry-ui"))
        }
    }
}
