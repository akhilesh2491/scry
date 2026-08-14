/**
 * Convention for Scry library modules that target every platform.
 *
 * Note: AGP 9 rejects `com.android.library` alongside the Kotlin Multiplatform
 * plugin. `com.android.kotlin.multiplatform.library` is the supported path, and
 * it contributes the Android target itself (via the `androidLibrary` extension)
 * rather than requiring a separate `androidTarget()` call.
 */

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("scry.publish")
}

configureScryLibrary(withIos = true)
