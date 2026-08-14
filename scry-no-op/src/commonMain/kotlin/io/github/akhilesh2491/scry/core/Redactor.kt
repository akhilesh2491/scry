package io.github.akhilesh2491.scry.core

/**
 * Lives in `Redactor.kt` on purpose.
 *
 * Kotlin compiles top-level declarations into a facade class named after the
 * *file* (`RedactorKt`), so a no-op that puts this constant in a differently
 * named file is not source-compatible for Java callers even though the Kotlin
 * name is identical. Mirror real file names whenever a file has public
 * top-level declarations.
 */
public const val REDACTED: String = "\u2588\u2588 redacted by Scry \u2588\u2588"
