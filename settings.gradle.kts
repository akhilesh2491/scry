rootProject.name = "scry"

pluginManagement {
    includeBuild("build-logic")
    includeBuild("scry-gradle-plugin")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// --- Library modules -------------------------------------------------------
include(":scry-core")
// Uncommented as each module lands (M1 sequence in the plan):
include(":scry-ui")
include(":scry-network")
include(":scry-prefs")
include(":scry-database")
include(":scry-crash")
include(":scry-logs")
include(":scry-network-ktor")
include(":scry-network-okhttp")
include(":scry-no-op")

// --- Samples ---------------------------------------------------------------
include(":samples:sample-android")
include(":samples:sample-desktop")
include(":samples:sample-ios")
