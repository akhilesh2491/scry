plugins {
    id("scry.library.jvm")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":scry-network"))
        }
        // OkHttp is JVM-only, so the interceptor lives in the shared JVM source
        // set and the module simply has nothing to offer other targets.
        val jvmSharedMain by getting {
            dependencies {
                api(libs.okhttp)
            }
        }
        val jvmSharedTest by getting {
            dependencies {
                implementation(libs.okhttp.mockwebserver)
            }
        }
    }
}
