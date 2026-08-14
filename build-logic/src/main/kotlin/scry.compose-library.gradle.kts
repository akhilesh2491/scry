/**
 * Convention for Scry modules that ship Compose Multiplatform UI.
 *
 * Only applies the plugins; each module declares which Compose artifacts it
 * actually needs, because the `compose.*` dependency accessors are only
 * available inside a module's own build script.
 */

plugins {
    id("scry.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}
