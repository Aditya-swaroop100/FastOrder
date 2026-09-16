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
| Backend | Firebase — Auth, Firestore, Storage |
| AI | Firebase AI Logic (Gemini) — no API key shipped in the app |
| Monitoring | Crashlytics · Analytics |
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
└── ui/
    ├── navigation/
    │   ├── Route.kt             @Serializable type-safe routes
    │   └── FastOrderNavHost.kt  the app's one navigation graph
    ├── screens/
    │   ├── BrandSplashScreen.kt animated launch screen
    │   ├── OnboardingScreen.kt
    │   ├── SignInScreen.kt
    │   └── HomeScreen.kt
    └── theme/                   Material 3 theme
```

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

## Status

🚧 **Early development.**

| Area | State |
|---|---|
| Project scaffolding, version catalog, Firebase wiring | ✅ Done |
| Branded splash (system + in-app animated) | ✅ Done |
| Navigation graph, type-safe routes | ✅ Done |
| Firebase Auth sign-in | ⬜ Not started |
| Firestore catalog + product browse | ⬜ Not started |
| Cart & checkout | ⬜ Not started |
| Ingredient-powered search | ⬜ Not started |
| Recipe link → ingredient list (Gemini) | ⬜ Not started |
| App Check | ⬜ Not started |

Screens currently behind the splash are placeholders.

