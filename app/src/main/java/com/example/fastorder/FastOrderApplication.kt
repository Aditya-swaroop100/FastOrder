package com.example.fastorder

import android.app.Application
import android.util.Log
import com.example.fastorder.logging.CrashlyticsTree
import dagger.hilt.android.HiltAndroidApp
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid
import timber.log.Timber

/**
 * Application entry point. Exists purely to initialise process-wide monitoring.
 *
 * Firebase (Auth, Analytics, Crashlytics) self-initialises from its own
 * `ContentProvider` before this class runs, so there is nothing to do for it
 * here. Sentry is initialised manually instead of through its auto-init
 * provider so that the options below - in particular the quota-related ones -
 * are in force for the very first event rather than applied after the fact.
 *
 * `@HiltAndroidApp` generates the `SingletonComponent` and attaches it to this
 * Application. There is no `initialise()` call to make and nothing to remember
 * to call in order: the component is built lazily on the first injection, and
 * every `@Provides` in the graph is `by lazy` in effect for free.
 *
 * Registered in `AndroidManifest.xml` via `android:name`.
 *
 * Crashlytics and Sentry both install an uncaught-exception handler, and they
 * coexist deliberately: Sentry's handler chains to whichever handler was
 * already installed, so a crash is reported to both rather than either one
 * swallowing it.
 */
@HiltAndroidApp
class FastOrderApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Trees first: anything logged during Sentry init should still reach
        // logcat. Sentry's own tree is planted by initSentry() below, so calls
        // made before that point are local-only - which is fine, since there is
        // nowhere to send them yet.
        plantLoggingTrees()
        initSentry()

        Timber.i("Application started: environment=%s", BuildConfig.SENTRY_ENVIRONMENT)
    }

    /**
     * Installs the Timber trees for this process.
     *
     * Three trees can end up planted, each with a distinct job:
     *
     *  - [Timber.DebugTree] - debug builds only, writes to logcat.
     *  - [CrashlyticsTree]  - always, mirrors INFO+ into Crashlytics so its
     *    crash reports carry the same breadcrumb trail Sentry gets.
     *  - `SentryTimberTree` - **not planted here**. The Sentry SDK installs it
     *    itself during [initSentry] because `sentry-android-timber` is on the
     *    classpath. Planting a second one manually would double-report every
     *    call, so it is deliberately left alone.
     */
    private fun plantLoggingTrees() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(CrashlyticsTree())
    }

    /**
     * Configures Sentry for the free **Developer** plan.
     *
     * That plan meters each event category separately - errors, spans
     * (performance), replays and attachments all have their own allowance - so
     * everything except errors is switched off explicitly below. Most of these
     * are off by default anyway, but leaning on a default for something that
     * consumes quota is how a free plan quietly runs out mid-sprint. Each block
     * notes what to change to turn the feature back on.
     */
    private fun initSentry() {
        val dsn = BuildConfig.SENTRY_DSN

        // No DSN configured (fresh clone, or CI without the env var). Skipping
        // init leaves every Sentry API call a no-op, so the app runs normally.
        if (dsn.isBlank()) {
            Log.i(TAG, "Sentry disabled: no DSN. Add `sentry.dsn=...` to local.properties.")
            return
        }

        SentryAndroid.init(this) { options ->
            options.dsn = dsn

            // --- Identification -------------------------------------------
            // Lets the Sentry UI separate dev noise from production traffic and
            // attribute each issue to a specific build.
            options.environment = BuildConfig.SENTRY_ENVIRONMENT
            options.release =
                "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"

            // --- Errors: the one category actually in use -----------------
            // 1.0 = report every error. The free plan allows 5k errors/month,
            // which a single developer is not realistically going to exhaust.
            options.sampleRate = 1.0
            options.isAnrEnabled = true
            options.maxBreadcrumbs = 100

            // --- Structured logs ------------------------------------------
            // Off by default in the SDK, so it must be opted into explicitly.
            // This is what makes Timber output show up under Sentry's "Logs"
            // explorer as a browsable stream, rather than only appearing as
            // breadcrumbs attached to some later error. Every Sentry plan,
            // including the free Developer one, includes 5GB of logs per month,
            // and because no pay-as-you-go budget is configured on the account,
            // anything past that allowance is dropped rather than billed.
            //
            // SentryTimberIntegration (installed automatically, see
            // plantLoggingTrees) maps Timber calls onto Sentry with these
            // defaults, which are exactly the thresholds wanted here:
            //
            //   Timber.e()          -> Sentry *issue*      (minEventLevel      = ERROR)
            //   Timber.i()/.w()/.e()-> Sentry *breadcrumb* (minBreadcrumbLevel = INFO)
            //   Timber.i()/.w()/.e()-> Sentry *log*        (minLogsLevel       = INFO)
            //   Timber.d()/.v()     -> logcat only, never leaves the device
            //
            // Keeping debug/verbose local is what stops a chatty render loop
            // from turning into log volume.
            options.logs.isEnabled = true

            // --- Performance / tracing: ON --------------------------------
            // Spans are their own billing category, but the Developer plan
            // includes 5M of them per month - the same allowance Team and
            // Business get - and that plan cannot hold a pay-as-you-go budget,
            // so anything past it is dropped rather than charged.
            //
            // Debug samples everything because the whole point during
            // development is to see the trace you just produced. Release is
            // sampled down: 5M spans is generous for one developer but not for
            // a real user base, and a transaction is worth many spans.
            options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.2

            // One transaction per click/scroll/swipe. This is the single
            // biggest span consumer here - set it to false first if the span
            // budget ever gets tight.
            options.isEnableUserInteractionTracing = true

            // Cold/warm start timing, Activity lifecycle transactions, and
            // slow/frozen frame counts. All default to on once tracing is
            // enabled; stated explicitly because they are the reason tracing
            // was turned on at all, and a silent default flip would be easy to
            // miss.
            options.isEnableAutoActivityLifecycleTracing = true
            options.isEnableFramesTracking = true

            // --- Session Replay: OFF --------------------------------------
            // The free plan includes only ~50 replays/month, and each replay
            // also uploads video frames against the attachment allowance.
            options.sessionReplay.sessionSampleRate = 0.0
            options.sessionReplay.onErrorSampleRate = 0.0

            // --- Attachments: OFF -----------------------------------------
            // Screenshots and view hierarchies are genuinely useful, but they
            // attach to every single error and eat the attachment quota fast.
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false

            // --- Release health -------------------------------------------
            // Sessions are not billed as events; this powers the crash-free
            // rate without touching the error allowance.
            options.isEnableAutoSessionTracking = true

            // --- Privacy ---------------------------------------------------
            // Keep IP addresses, headers and user identifiers out of events.
            options.isSendDefaultPii = false

            // --- The SDK's own logging ------------------------------------
            // Prints what Sentry is doing to logcat, on debug builds only, so
            // you can actually watch envelopes leave the device while
            // developing. Release builds stay silent.
            options.isDebug = BuildConfig.DEBUG
            options.setDiagnosticLevel(
                if (BuildConfig.DEBUG) SentryLevel.DEBUG else SentryLevel.WARNING
            )
        }

        Log.i(TAG, "Sentry initialised for environment=${BuildConfig.SENTRY_ENVIRONMENT}")
    }

    private companion object {
        const val TAG = "FastOrderApplication"
    }
}
