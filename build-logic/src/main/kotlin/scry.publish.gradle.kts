/**
 * Publishing convention for every released Scry artifact.
 *
 * Uses the Vanniktech plugin rather than hand-rolling `maven-publish`, because
 * KMP publishing means one root module plus one per target, each needing its own
 * POM, sources jar, javadoc jar and signature. Getting that right by hand is a
 * day of work and a recurring source of "why did Central reject this".
 *
 * Credentials are never read from the repository. They come from
 * `~/.gradle/gradle.properties` or, in CI, `ORG_GRADLE_PROJECT_*` environment
 * variables: mavenCentralUsername, mavenCentralPassword, signingInMemoryKey,
 * signingInMemoryKeyPassword.
 */

plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    // Central Portal is the only host in current plugin versions; SonatypeHost
    // is gone. `false` = stage the deployment but do not auto-release, so the
    // first few releases can be eyeballed in the portal before going public.
    publishToMavenCentral(automaticRelease = false)

    // Signing is required by Central. Skipped for local snapshot work, where no
    // key is configured, so `publishToMavenLocal` keeps working offline.
    if (project.hasProperty("signingInMemoryKey")) {
        signAllPublications()
    }

    coordinates(
        groupId = "io.github.akhilesh2491.scry",
        artifactId = project.name,
        version = project.version.toString(),
    )

    pom {
        name.set(project.name)
        description.set(
            "On-device debugging suite for Kotlin Multiplatform: network inspection, " +
                "preferences and SQLite editing, crash and ANR capture.",
        )
        url.set("https://github.com/akhilesh2491/scry")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("akhilesh2491")
                name.set("Akhilesh Swarnkar")
                url.set("https://github.com/akhilesh2491")
            }
        }
        scm {
            url.set("https://github.com/akhilesh2491/scry")
            connection.set("scm:git:git://github.com/akhilesh2491/scry.git")
            developerConnection.set("scm:git:ssh://git@github.com/akhilesh2491/scry.git")
        }
    }
}
