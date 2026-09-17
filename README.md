# FastOrder

> **From reel to meal, in minutes**

A quick-commerce grocery app for Android — think Zepto or Instamart — with two
things they don't have.

---

## What makes it different

### 🔍 Ingredient-powered search

Search the catalogue by what a product *contains*, not just what it's called.
Find items by ingredient, rule out things you can't eat, and surface substitutes
you'd never have thought to search for.

### 🔗 Recipe link → cart

Paste a YouTube, blog, or search link for any dish. Gemini reads the source and
returns a structured ingredient list. From there you can:

- **Add everything to the cart** in a single tap, or
- **Find similar** next to any line item, to swap in a different brand, size, or
  substitute

No more pausing a video every fifteen seconds to scribble down what to buy.

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.4 |
| UI | Jetpack Compose · Material 3 |
| Architecture | Single-activity · MVVM · type-safe Navigation Compose |
| DI | Hilt 2.60 on KSP — one compile-time-checked graph |
| Backend | Firebase — Auth, Firestore, Storage |
| AI | Firebase AI Logic (Gemini) — no API key shipped in the app |
| Monitoring | Crashlytics · Analytics · Sentry (free Developer plan) |
| Build | AGP 9.4 · Gradle 9.6 · version catalogs |

**Min SDK** 24 · **Target SDK** 37

---

## Architecture notes

**Single activity, deliberately.** `MainActivity` is the only `Activity` in the
app. Splash, onboarding, sign-in and the storefront are all destinations in one
Navigation Compose graph.

There is no `SplashActivity` or `LoginActivity`, because:

- Since Android 12 the system draws its own splash on every cold start and it
  can't be disabled — a splash `Activity` would show two splashes back to back
- Sign-in isn't only a startup concern (tokens expire, sessions get revoked), so
  it needs to be reachable mid-session as a normal navigation target
- The share-a-recipe-link flow is an `ACTION_SEND` intent filter plus a deep
  link, which is far simpler against a single activity

**Startup is gated, not guessed.** `MainViewModel` resolves the start
destination *before* the splash is dismissed, so the user never sees the
storefront flash before being redirected to sign-in.

**Two-stage launch.** The system splash covers cold start. An in-app
`BrandSplashScreen` then plays the branded animation — logo, sweeping line,
word-by-word tagline — because the system splash supports neither text nor
artwork outside its circular icon mask.

---

## Project layout

```
app/src/main/java/com/example/fastorder/
├── MainActivity.kt              single activity; hosts the nav graph
├── MainViewModel.kt             startup state + start-destination resolution
├── di/
│   ├── ApplicationScope.kt      @Qualifier for the process-lifetime scope
│   └── CoroutinesModule.kt      that scope's only binding
├── auth/                        ← feature slice, module-extraction ready
│   ├── di/
│   │   └── AuthModule.kt        the slice's own Hilt bindings
│   ├── domain/                  pure Kotlin; no Android, no Firebase
│   │   ├── AuthRepository.kt    the seam everything above depends on
│   │   ├── AuthUser.kt          immutable user model (not FirebaseUser)
│   │   ├── AuthError.kt         one case per user-visible failure
│   │   └── AuthResult.kt        typed outcome + OtpChallenge
│   ├── data/                    the only package importing Firebase Auth
│   │   ├── FirebaseAuthRepository.kt
│   │   ├── GoogleCredentialClient.kt  Credential Manager wrapper
│   │   └── AuthErrorMapper.kt   SDK exceptions → AuthError
│   └── ui/
│       ├── SignInScreen.kt      3 panes in 1 destination
│       ├── SignInViewModel.kt
│       ├── SignInUiState.kt
│       └── components/          ChooseMethod / PhoneNumber / Otp panes
└── ui/
    ├── navigation/
    │   ├── Route.kt             @Serializable type-safe routes
    │   └── FastOrderNavHost.kt  the app's one navigation graph
    ├── screens/
    │   ├── BrandSplashScreen.kt animated launch screen
    │   ├── OnboardingScreen.kt
    │   ├── HomeScreen.kt
    │   └── HomeViewModel.kt
    └── theme/                   Material 3 theme
```

---

## Authentication

Google Sign-In and phone/OTP, both through Firebase Auth.

### Why auth is a package, not a Gradle module — still

The textbook answer is `:feature:auth`. Hilt removed the objection that used to
head this list, but two remain:

- **No siblings to parallelise.** Gradle's build-time win comes from compiling
  *independent* modules simultaneously. One feature module has nothing to run
  alongside, so it buys configuration overhead and no speed.
- **The shared boundary isn't discovered yet.** `:feature:auth` only pays off
  once `:core:model`, `:core:ui` and `:core:data` exist to hold what features
  have in common. Extracting the first feature means inventing those seams from
  a sample size of one, and every later feature then bends to fit them.

What matters is the **seam**, and it's stronger than before:

- `auth/domain/AuthRepository` is a pure-Kotlin interface, and everything above
  it — ViewModels, screens, `MainViewModel` — depends only on it
- `auth/data/` is the sole package importing `com.google.firebase.auth`
- `auth/di/AuthModule.kt` contributes the slice's bindings to
  `SingletonComponent` **from inside the slice**, so the app module wires
  nothing by hand and names no concrete type

Extracting the module is therefore a directory move plus a `build.gradle.kts`,
with **no call-site changes**.

**Migration trigger:** the third feature. Cart and catalog are what will show
which types actually belong in `:core:*`; splitting before that is guesswork.

### Dependency injection: Hilt

`di/ServiceLocator.kt` is gone. It was honest while the graph had one binding,
but it carried two costs that grow with every feature:

- **ViewModels needed hand-written wiring.** Either a
  `ViewModelProvider.Factory` per ViewModel, or a constructor default reaching
  for the global — and a default argument means nothing prevents production code
  from quietly using the singleton it was supposed to be injectable around.
- **Composables could reach the container.** `HomeScreen` read the repository
  out of the global object inside its composable body, which made it neither
  testable nor previewable.

The graph now:

| Concern | Where |
|---|---|
| Process-lifetime `CoroutineScope` | `di/CoroutinesModule.kt`, behind `@ApplicationScope` |
| Firebase Auth + Credential Manager | `auth/di/AuthModule.kt` |
| ViewModels | `@HiltViewModel` + `@Inject constructor` |
| Injection points | `@HiltAndroidApp`, `@AndroidEntryPoint`, `hiltViewModel()` |

Two decisions worth keeping:

- **`@ApplicationScope` is a qualifier, not a bare `CoroutineScope` binding.** A
  repository handed a `viewModelScope` by mistake loses its auth-state
  subscription the moment a screen leaves — exactly when the resulting session
  matters most. A qualifier makes that a compile-time choice rather than an
  assumption.
- **`FirebaseAuthRepository` and `GoogleCredentialClient` stay `internal`.**
  `AuthModule` builds both inside a single `@Provides` instead of binding each
  one separately, so no Firebase or Credential Manager type ever appears in a
  signature visible outside `auth/data`.

**KSP, not kapt.** kapt generates a Java stub for every Kotlin file in the
module before the processor even runs; KSP reads the Kotlin symbols directly.

Testing is unchanged, which is the point: `SignInViewModelTest` still runs 11
cases against a hand-written fake on a plain JVM — no emulator, no Firebase
project, no real SMS, and no Hilt. The constructor is the only injection point,
so a test needs nothing from the framework to replace a dependency.


### Why Credential Manager

`GoogleSignInClient` and One Tap are deprecated. Credential Manager is the
current API, and it fronts Google accounts, saved passwords **and passkeys**
behind one system sheet — so passkeys later become an extra option on the
existing request rather than a second sign-in stack.

Google sign-in escalates through three requests, quietest first:

1. **Authorized accounts only** — returning users get a one-tap sheet
2. **Any account on the device** — reinstalls and new devices
3. **Sign in with Google** — branded flow; the only one offering "add account"

Only `NoCredentialException` advances the cascade. A user who *dismisses* the
sheet is answered, not re-prompted — retrying on cancellation would trap them in
a loop of dialogs they can't escape.

### Phone auth notes

- `sendOtp` may return **`AutoVerified`** instead of `CodeSent`: Play services
  can verify a number with no SMS at all. Modelling that explicitly is what stops
  the app showing an OTP box for a code that will never arrive.
- Auto-retrieval can also land *after* the OTP pane is already up. The repository
  completes that sign-in on the application scope, and the UI notices because it
  observes auth state rather than a return value.
- **Resend replays Firebase's resend token.** Calling `sendOtp` again instead
  would open a second verification and count twice against the per-number abuse
  quota — which is how a user tapping "resend" twice gets locked out.

### Passkeys — deliberately skipped

Firebase Auth has **no passkey/WebAuthn provider**. Real passkeys would need:

- a WebAuthn server (challenge generation, attestation verification, credential
  storage) → Cloud Functions + Firestore
- minting a Firebase **custom token** via the Admin SDK → `signInWithCustomToken`
- a real domain serving `/.well-known/assetlinks.json`
- **Blaze billing** (this project is on Spark)

That's a backend project, not a client feature. The Credential Manager
foundation above means adding it later is additive.

### Firebase setup that's already done

| Item | Status |
|---|---|
| Debug SHA-1 + SHA-256 registered | ✅ |
| Android OAuth client created | ✅ |
| Google provider enabled (`firebase.json` → `deploy --only auth`) | ✅ |
| Phone provider enabled (console — no CLI support) | ✅ |

**Phone sign-in can't be enabled from the CLI**, so it was switched on by hand
at *Authentication → Sign-in method → Phone* in the
[Firebase console](https://console.firebase.google.com/project/fastorder-f7ce0/authentication/providers).

While developing, add **test phone numbers** on that same screen. They return a
fixed code, skip SMS entirely, and don't touch the Spark plan's daily quota —
which is small enough to exhaust in an afternoon of real testing.

When adding a release keystore or Play App Signing, register those fingerprints
too, then re-download the config:

```bash
npx -y firebase-tools@latest apps:sdkconfig ANDROID <APP_ID> --out app/google-services.json
```

A missing fingerprint surfaces as `AuthError.AppVerificationFailed` — that case
exists precisely to make this diagnosable from a log line.

---

## Getting started

```bash
git clone https://github.com/Aditya-swaroop100/FastOrder.git
cd FastOrder
./gradlew :app:assembleDebug
```

`app/google-services.json` is committed, so the project builds without extra
setup. It holds identifiers — project ID, app ID, API key — not credentials;
the same values ship inside every published APK. Access is controlled by
Firestore Security Rules, App Check, and API key restrictions.

To point the app at your own Firebase project instead:

```bash
npx -y firebase-tools@latest apps:sdkconfig ANDROID <YOUR_APP_ID> \
  --project <YOUR_PROJECT_ID> --out app/google-services.json
```

---

## Error monitoring (Sentry)

Sentry runs on the free **Developer** plan and is entirely optional — with no
DSN configured the SDK is never initialised and the app builds and runs exactly
as before. To switch it on:

1. Create a project at [sentry.io](https://sentry.io) → **Create Project** →
   **Android**. The Developer plan is free and needs no card.
2. Copy the DSN it hands you.
3. Add it to `local.properties`, which is git-ignored:

   ```properties
   sentry.dsn=https://<key>@o<org>.ingest.<region>.sentry.io/<project>
   ```

4. Rebuild and run. `FastOrderApplication` logs
   `Sentry initialised for environment=development` on startup.

The DSN can equally come from `-Psentry.dsn=...` or a `SENTRY_DSN` environment
variable, which is how CI would inject it. A DSN is not a credential — it ships
inside every APK and only grants permission to *write* events — but it is
account-specific, so it is kept out of the repo rather than hard-coded.

### Staying inside the free tier

The Developer plan meters errors, spans, replays and attachments as **separate**
allowances. `FastOrderApplication` therefore enables errors and ANRs only, and
explicitly pins the billable extras to off:

| Feature | State | To enable |
|---|---|---|
| Errors + ANRs | ✅ on (`sampleRate = 1.0`) | — |
| Structured logs | ✅ on (`logs.isEnabled`) | — |
| Release health / sessions | ✅ on (not billed as events) | — |
| Performance tracing | ✅ on (1.0 debug / 0.2 release) | — |
| User interaction tracing | ✅ on | — |
| Session Replay | ⬜ off | raise `sessionReplay.*SampleRate` |
| Screenshots / view hierarchy | ⬜ off | `isAttachScreenshot = true` |
| Profiling | ⬜ off | not possible on this plan — see below |

### Logging (Timber → Sentry + Crashlytics)

Logging goes through [Timber](https://github.com/JakeWharton/timber). Call sites
use `Timber.i("Startup: ready in %d ms", elapsed)` rather than string
interpolation on purpose: Sentry stores the **format template** as a separate
attribute, so `"Startup: ready in %d ms"` groups as one log regardless of the
value substituted in. Interpolating first would produce a distinct log per
measurement and make the stream unfilterable.

Three trees are planted (see `FastOrderApplication.plantLoggingTrees`):

| Tree | Builds | Destination |
|---|---|---|
| `Timber.DebugTree` | debug only | logcat |
| `CrashlyticsTree` | all | Crashlytics `log()`, plus `recordException()` for ERROR + throwable |
| `SentryTimberTree` | all | Sentry — **not planted by us** |

That last one is installed by the Sentry SDK itself during init, because
`sentry-android-timber` is on the classpath. Planting a second one manually
would double-report every call, so it is deliberately left alone. Its default
thresholds are exactly what is wanted here:

| Timber call | Becomes |
|---|---|
| `Timber.e()` | a Sentry **issue** (`minEventLevel = ERROR`) |
| `Timber.i()` / `.w()` / `.e()` | a Sentry **breadcrumb** (`minBreadcrumbLevel = INFO`) |
| `Timber.i()` / `.w()` / `.e()` | a Sentry **log** (`minLogsLevel = INFO`) |
| `Timber.d()` / `.v()` | logcat only — never leaves the device |

Keeping debug/verbose local is what stops a chatty render loop from turning into
log volume.

Structured logs are a separate Sentry product from errors, and are **off by
default in the SDK** — `options.logs.isEnabled = true` is what makes Timber
output show up under Sentry's *Logs* explorer as a browsable stream rather than
only appearing as breadcrumbs attached to some later error. Every Sentry plan,
the free Developer one included, comes with 5GB of logs per month, and with no
pay-as-you-go budget configured on the account anything past that allowance is
dropped rather than billed.

Log messages deliberately carry no user identifiers: `isSendDefaultPii` is
`false`, and putting an email or uid into a message would route straight around
that setting.

### Tracing

Tracing is on. The Developer plan includes **5M spans/month** — the same
allowance Team and Business get — and that plan cannot hold a pay-as-you-go
budget, so anything beyond it is dropped rather than charged.

`tracesSampleRate` is `1.0` on debug and `0.2` on release. Sampling everything
during development is the point; release is sampled down because 5M spans is
generous for one developer but not for a real user base, and a single
transaction is worth many spans.

What gets instrumented:

| Source | Produces |
|---|---|
| App start | `app.start.cold` / `.warm`, `process.load` |
| Activity lifecycle | `activity.load`, `ui.load.initial_display` |
| Frames tracking | `frames_total`, `frames_slow`, `frames_frozen`, `frames_delay` |
| Navigation Compose | one transaction per destination |
| User interaction | one transaction per click/scroll/swipe |

Navigation tracing needs `sentry-compose-android`, wired through
`rememberNavController().withSentryObservableEffect()` in `FastOrderNavHost`.
Without it, a single-Activity Compose app reports **one** transaction for the
entire session with every screen collapsed inside it — Activity-level
instrumentation cannot see Compose destinations.

One thing to expect while the screens are still placeholders: Sentry creates a
navigation transaction per destination but **discards any that finish with no
child spans**, so you will see

```
Dropping idle transaction /...Route.Home because it has no child spans
```

That is correct behaviour, not a misconfiguration — an empty transaction
carries no information and would only burn quota. Once a screen does real work
(a Firestore read, an HTTP call), that work becomes a child span and the
transaction is kept.

User interaction tracing is the largest span consumer here. If the span budget
ever gets tight, set `isEnableUserInteractionTracing = false` before touching
`tracesSampleRate` — it removes the chattiest source while leaving app start
and navigation timing intact.

### Why profiling is off

Profiling is the one Sentry product that genuinely cannot run on this plan, and
it is worth writing down so nobody re-checks it later.

Unlike errors, logs and spans — all of which have a free monthly allowance —
profiling has **no included volume on any plan**. Sentry's own pricing
calculator, configured for the paid $26/mo Team plan, lists:

```
Cont. profile hrs   0 hr   $0.00
UI profile hrs      0 hr   $0.00
```

Profile hours are sold *only* through a pay-as-you-go budget
(`$0.25/hr` UI, `$0.0315/hr` continuous), and a pay-as-you-go budget requires a
paid plan — a Developer-plan org has to upgrade to Team or Business before it
can set one. In the plan comparison table the Developer column for both
*UI Profiling* and *Continuous Profiling* is simply blank, where every other row
carries a value.

So there is no sampling rate that makes profiling free here. Nothing is set in
`FastOrderApplication`, which leaves `profilesSampleRate` and
`profileSessionSampleRate` at their `null` default and the profiler disabled.

**The one free route** is a *one-time 14-day product trial*, which Sentry offers
to free accounts at any time from
[Settings → Subscription](https://fastorder.sentry.io/settings/billing/overview/).
Trial data is not billed. If you start one, profiling turns on with:

```kotlin
// Continuous profiling (Sentry 8.x). Requires tracing, which is already on.
options.profileSessionSampleRate = 1.0
options.profileLifecycle = ProfileLifecycle.TRACE
options.isStartProfilerOnAppStart = true
```

Remember to remove it when the trial ends — afterwards the profiler keeps
collecting and uploading data that the account has no budget to accept, so it
costs battery and bandwidth for payloads Sentry will reject.

### Why there is no Sentry Gradle plugin

The plugin's headline features — ProGuard mapping upload, source context,
dependency reporting — all call Sentry's API *during the build* and require a
`SENTRY_AUTH_TOKEN`. That would break a clean checkout for anyone who doesn't
have one, for no benefit on a dev-only plan. The trade-off: release stack traces
in Sentry are obfuscated (Crashlytics already receives the mapping file, so
deobfuscated release crashes are covered there). Debug builds are unminified and
read fine.

Sentry and Crashlytics coexist deliberately — Sentry's uncaught-exception
handler chains to the one Crashlytics already installed, so a crash reaches
both rather than either swallowing it.

---

## Status

🚧 **Early development.**

| Area | State |
|---|---|
| Project scaffolding, version catalog, Firebase wiring | ✅ Done |
| Branded splash (system + in-app animated) | ✅ Done |
| Sentry error monitoring (free tier) | ✅ Done |
| Timber logging → Sentry logs + breadcrumbs | ✅ Done |
| Sentry tracing (app start, navigation, frames) | ✅ Done |
| Navigation graph, type-safe routes | ✅ Done |
| Firebase Auth sign-in | ⬜ Not started |
| Firestore catalog + product browse | ⬜ Not started |
| Cart & checkout | ⬜ Not started |
| Ingredient-powered search | ⬜ Not started |
| Recipe link → ingredient list (Gemini) | ⬜ Not started |
| App Check | ⬜ Not started |

Screens currently behind the splash are placeholders.

