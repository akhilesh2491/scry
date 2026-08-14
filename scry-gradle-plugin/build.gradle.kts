plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
    alias(libs.plugins.gradlePluginPublish)
}

group = "io.github.akhilesh2491.scry"
version = "0.1.0"

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

gradlePlugin {
    // Required by the Plugin Portal; publishing fails validation without both.
    website = "https://github.com/akhilesh2491/scry"
    vcsUrl = "https://github.com/akhilesh2491/scry.git"

    plugins {
        create("scry") {
            id = "io.github.akhilesh2491.scry"
            implementationClass = "io.github.akhilesh2491.scry.gradle.ScryGradlePlugin"
            displayName = "Scry"
            description = "Wires Scry into debug builds and keeps it out of release builds."
            tags = listOf("android", "kotlin-multiplatform", "debugging", "network-inspector", "developer-tools")

            // This plugin IS configuration-cache compatible — `checkScryNotInRelease`
            // reuses a cache entry across runs, because ScryReleaseLeakCheckTask
            // resolves component identifiers to strings at configuration time so
            // nothing unserializable reaches execution.
            //
            // It is not declared here because plugin-publish 2.1.1 exposes no
            // `compatibility { features { ... } }` DSL, despite what
            // https://plugins.gradle.org/docs/publish-plugin documents. Set it in
            // the Plugin Portal web UI, or add the block once a release ships it.
        }
    }
}

/**
 * Bakes the plugin's own version into a resource.
 *
 * The plugin adds dependencies on the matching Scry artifacts, so it has to know
 * its version at execution time. Reading it from a generated resource keeps the
 * plugin and the libraries impossible to mismatch.
 */
val generateVersionResource by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/scry-version")
    val versionValue = project.version.toString()
    inputs.property("version", versionValue)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("scry-version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$versionValue\n")
    }
}

sourceSets.main {
    resources.srcDir(generateVersionResource)
}

dependencies {
    compileOnly(libs.plugin.android.gradle)
    testImplementation(libs.kotlin.test)
    testImplementation(gradleTestKit())
}

tasks.test {
    useJUnitPlatform()
}
