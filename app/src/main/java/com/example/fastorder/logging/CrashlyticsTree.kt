package com.example.fastorder.logging

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * Mirrors Timber output into Firebase Crashlytics.
 *
 * Sentry gets a breadcrumb trail for free via `SentryTimberIntegration`, which
 * the Sentry SDK plants during init. Crashlytics has no equivalent hook, so
 * without this tree its crash reports would arrive with a stack trace and no
 * context, while Sentry's showed the full lead-up to the same crash. Since this
 * app deliberately reports to both, they should carry the same evidence.
 *
 * Crashlytics buffers these `log` entries on-device and only uploads them when
 * attached to a report, so a quiet run costs nothing but a ring buffer.
 *
 * @see FastOrderApplication.plantLoggingTrees for where this is installed.
 */
class CrashlyticsTree : Timber.Tree() {

    private val crashlytics by lazy { FirebaseCrashlytics.getInstance() }

    /**
     * Drops DEBUG and VERBOSE before any work happens.
     *
     * Those levels exist for tight loops and render passes; recording them
     * would flood the report buffer and push out the entries that actually
     * explain a crash. Matches the INFO threshold Sentry's tree uses, so both
     * services see the same set of messages.
     */
    override fun isLoggable(tag: String?, priority: Int): Boolean =
        priority >= Log.INFO

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        crashlytics.log(
            buildString {
                append(priorityLabel(priority))
                append('/')
                append(tag ?: "FastOrder")
                append(": ")
                append(message)
            }
        )

        // A throwable at ERROR is a genuine fault worth surfacing as its own
        // non-fatal in Crashlytics, mirroring how Timber.e() becomes a distinct
        // issue in Sentry. Lower levels stay as plain log lines: an exception
        // that was handled and warned about is context, not an incident.
        if (t != null && priority >= Log.ERROR) {
            crashlytics.recordException(t)
        }
    }

    private fun priorityLabel(priority: Int): String = when (priority) {
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        Log.ASSERT -> "A"
        else -> priority.toString()
    }
}
