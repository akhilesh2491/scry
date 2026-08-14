/**
 * Convention for Scry modules whose implementation is inherently JVM-only.
 *
 * The OkHttp adapter is the case that motivates this: OkHttp does not exist on
 * iOS, and shipping an empty iOS klib would advertise support the module cannot
 * provide. Ktor-based apps get iOS coverage through `scry-network-ktor` instead.
 */

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("scry.publish")
}

configureScryLibrary(withIos = false)
