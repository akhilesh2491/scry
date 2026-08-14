plugins {
    id("scry.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // ScryFileProvider references R.xml.scry_file_paths.
    androidLibrary {
        androidResources.enable = true
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.sqlite)
            implementation(libs.androidx.sqlite.bundled)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            // FileProvider, for sharing exports without a huge intent extra.
            implementation(libs.androidx.core.ktx)
        }
    }
}
