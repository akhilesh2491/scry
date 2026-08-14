package io.github.akhilesh2491.scry.sample;

import io.github.akhilesh2491.scry.core.PlatformContext;
import io.github.akhilesh2491.scry.core.Retention;
import io.github.akhilesh2491.scry.core.Scry;
import io.github.akhilesh2491.scry.core.ScryInstance;
import io.github.akhilesh2491.scry.network.NetworkPlugin;
import io.github.akhilesh2491.scry.network.ktor.ScryKtorKt;
import okhttp3.OkHttpClient;
import io.github.akhilesh2491.scry.network.okhttp.ScryInterceptor;

/**
 * Compiles as Java, on purpose.
 *
 * Requirement #1 for Scry is that Kotlin, Java and KMP callers can all use it.
 * Java interop is easy to claim and easy to break — a Kotlin default argument or
 * a stray suspend function in the public API is invisible until someone tries it
 * from Java. This file fails the build the moment that happens, which is the
 * only way the claim stays true.
 *
 * Nothing calls this; it exists to be compiled.
 */
public final class JavaInteropSample {

    private JavaInteropSample() {
    }

    /** The builder facade — no Kotlin DSL, no lambdas with receivers. */
    public static ScryInstance installFromJava() {
        PlatformContext context = new PlatformContext("scry-java-sample");

        return Scry.installer(context)
                .retention(Retention.ofHours(12))
                .allowInReleaseBuilds(false)
                .redactHeaders("X-Internal-Trace")
                .redactBodyKeys("ssn")
                .addPlugin(new NetworkPlugin())
                .install();
    }

    /** Static entry points must be callable without {@code Scry.INSTANCE}. */
    public static void controlFromJava() {
        if (Scry.isInstalled()) {
            Scry.show();
            Scry.clear();
            Scry.hide();
        }
    }

    /** The OkHttp adapter, which is how most Android/Retrofit codebases integrate. */
    public static OkHttpClient okHttpClientFromJava() {
        return new OkHttpClient.Builder()
                .addInterceptor(new ScryInterceptor())
                .build();
    }

    /** Retention's Java-friendly factory, avoiding kotlin.time.Duration. */
    public static Retention retentionFromJava() {
        return Retention.ofHours(6);
    }

    /** The Ktor plugin object is reachable from Java too, though rarely needed there. */
    public static Object ktorPluginFromJava() {
        return ScryKtorKt.getScryKtor();
    }
}
