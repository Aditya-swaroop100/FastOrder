import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    // KSP must be applied before Hilt: Hilt's plugin registers its processor
    // against KSP's task graph, and reverses the order at its peril.
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Sentry DSN, resolved at configuration time from, in order:
 *
 *  1. `-Psentry.dsn=...` on the Gradle command line
 *  2. the `SENTRY_DSN` environment variable (how CI would supply it)
 *  3. `sentry.dsn=...` in `local.properties` (git-ignored - the normal dev path)
 *
 * A DSN is not a secret the way a signing key is: it ships inside every APK and
 * only grants permission to *write* events. It is still account-specific, so it
 * is kept out of the repo rather than hard-coded. When it resolves to empty the
 * SDK is simply never initialised, and the app builds and runs as normal.
 */
val sentryDsn: String =
    providers.gradleProperty("sentry.dsn").orNull
        ?: providers.environmentVariable("SENTRY_DSN").orNull
        ?: rootProject.file("local.properties")
            .takeIf { it.exists() }
            ?.let { f -> Properties().apply { f.inputStream().use(::load) } }
            ?.getProperty("sentry.dsn")
        ?: ""

/**
 * Whether debug builds skip Firebase's phone app-verification.
 *
 * With it on, `verifyPhoneNumber` goes straight to the server. With it off,
 * every send first probes for a reCAPTCHA Enterprise site key (~1.5s, and this
 * project has none) and then fetches a Play Integrity token (~1.3s) - around
 * five seconds of ceremony before the code is even requested. Test phone
 * numbers skip the SMS but *not* that, because the client cannot know a number
 * is a test number until the server tells it.
 *
 * Only meaningful alongside test numbers registered in the Firebase console;
 * with verification off, real numbers are rejected. Flip it with
 * `fastorder.auth.appVerification=true` in local.properties when you need to
 * test against an actual handset.
 *
 * Release builds ignore this entirely - see the buildTypes block below.
 */
val debugAppVerification: Boolean =
    (providers.gradleProperty("fastorder.auth.appVerification").orNull
        ?: providers.environmentVariable("FASTORDER_AUTH_APP_VERIFICATION").orNull
        ?: rootProject.file("local.properties")
            .takeIf { it.exists() }
            ?.let { f -> Properties().apply { f.inputStream().use(::load) } }
            ?.getProperty("fastorder.auth.appVerification")
        ?: "false").toBoolean()

android {
    namespace = "com.example.fastorder"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.fastorder"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Read back in FastOrderApplication. Empty string == Sentry stays off.
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
    }

    buildTypes {
        debug {
            // Keeps dev noise in its own bucket in Sentry's "environment" facet.
            buildConfigField("String", "SENTRY_ENVIRONMENT", "\"development\"")

            // See `debugAppVerification` above.
            buildConfigField(
                "boolean",
                "DISABLE_PHONE_APP_VERIFICATION",
                "${!debugAppVerification}",
            )
        }
        release {
            buildConfigField("String", "SENTRY_ENVIRONMENT", "\"production\"")

            // Hard-coded, not derived: app verification is what stops a
            // stranger burning the project's SMS quota, and a stray gradle
            // property must never be able to switch it off in a shipped build.
            buildConfigField("boolean", "DISABLE_PHONE_APP_VERIFICATION", "false")
            optimization {
                enable = true
            }
            // R8 is on for release, so stack traces reaching Crashlytics are
            // obfuscated unless the mapping file is uploaded alongside the build.
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // Needed so SENTRY_DSN / SENTRY_ENVIRONMENT reach the app as constants.
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Import the Firebase BoM
    implementation(platform(libs.firebase.bom))

    // Firebase Auth & Crashlytics SDKs (versions managed by the BoM)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.crashlytics)

    // Recommended: Analytics for Crashlytics breadcrumbs
    implementation(libs.firebase.analytics)

    // --- Dependency injection -------------------------------------------
    // Hilt replaces the hand-rolled ServiceLocator. The win is not fewer
    // lines, it is that the graph is now checked at compile time and that
    // ViewModels get constructor injection - so a screen never names a
    // factory, and a test never has to reach around a global singleton.
    //
    // It is also the precondition for splitting :feature:* Gradle modules
    // later: each module contributes its own @Module to the same
    // SingletonComponent, and the app module wires nothing by hand.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // hiltViewModel(), which resolves a @HiltViewModel from the current
    // NavBackStackEntry - i.e. scoped to the destination, not the Activity.
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // --- Sign-in -------------------------------------------------------
    // Credential Manager is the current system-level sign-in API; the old
    // GoogleSignInClient / One Tap (`play-services-auth`) surfaces are
    // deprecated. It fronts Google accounts, saved passwords and passkeys
    // behind one bottom sheet, which is why the Google button below and any
    // future passkey support share a single code path.
    //
    // `credentials-play-services-auth` is what actually services the request
    // on API < 34 (Android 13 and below have no system credential provider),
    // so it is required in practice, not optional.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)

    // Builds the Google-specific request options and parses the returned
    // credential Bundle. The ID token it yields is exchanged for a Firebase
    // session via GoogleAuthProvider.
    implementation(libs.googleid)

    // Task.await(). Firebase Auth is callback/Task based; this keeps the
    // repository written as plain suspend functions.
    implementation(libs.kotlinx.coroutines.play.services)

    // Sentry - SDK only, deliberately without the Sentry Gradle plugin.
    // That plugin's headline features (ProGuard mapping upload, source
    // context, dependency reporting) all call Sentry's API during the build
    // and need a SENTRY_AUTH_TOKEN, which would break clean checkouts and CI
    // for no gain on a dev-only free plan. Init happens in FastOrderApplication.
    implementation(libs.sentry.android)

    // Timber for logging, plus Sentry's Timber bridge. With both on the
    // classpath the Sentry SDK installs SentryTimberIntegration during
    // init, which plants a tree forwarding Timber calls to Sentry. See
    // FastOrderApplication for the level thresholds that tree applies.
    implementation(libs.timber)
    implementation(libs.sentry.android.timber)

    // Compose/Navigation tracing. Supplies withSentryObservableEffect(), which
    // turns each Navigation Compose destination change into a Sentry
    // transaction. Without it, tracing would only ever see Activity and app
    // start - which in a single-Activity Compose app means one transaction for
    // the whole session and no visibility into individual screens.
    implementation(libs.sentry.compose.android)
}

