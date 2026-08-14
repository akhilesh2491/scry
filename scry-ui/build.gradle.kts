plugins {
    id("scry.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":scry-core"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            // `api`, not `implementation`: plugin modules build their own screens
            // against the same icon set the shell uses.
            api(compose.materialIconsExtended)
        }
        androidMain.dependencies {
            // ComponentActivity + setContent. The only Android dependency the
            // UI module needs; no AppCompat, no Material components.
            api(libs.androidx.activity.compose)
            // WindowCompat, for status-bar appearance.
            implementation(libs.androidx.core.ktx)
        }
        // Desktop windowing lives in the desktop artifact; `common` rather than
        // `currentOs` so the published library is not pinned to the build host.
        val desktopMain by getting {
            dependencies {
                api(compose.desktop.common)
            }
        }
    }
}
