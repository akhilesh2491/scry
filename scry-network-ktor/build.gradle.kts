plugins {
    id("scry.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":scry-network"))
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
