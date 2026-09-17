package com.example.fastorder.auth.domain

/**
 * The app's own view of a signed-in user.
 *
 * Deliberately *not* `FirebaseUser`. Everything above the data layer talks to
 * this type, which means:
 *
 *  - Screens and ViewModels never import a Firebase class, so swapping the
 *    identity provider (or stubbing it in tests) touches one file.
 *  - It is immutable. `FirebaseUser` is a live handle whose fields change under
 *    you after a token refresh or profile update, which makes it unusable as
 *    Compose state - equal-but-mutated instances do not trigger recomposition.
 *
 * @param uid Stable across providers and across re-installs. This is the only
 *   field safe to use as a database key; phone numbers and emails can be
 *   changed or re-assigned to a different person by the carrier/provider.
 * @param providers Every provider linked to this account, not just the one used
 *   for the current sign-in. A user who first signed in with a phone number and
 *   later linked Google has both.
 */
data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val phoneNumber: String?,
    val photoUrl: String?,
    val isAnonymous: Boolean,
    val providers: Set<AuthProvider>,
)

/**
 * Identity providers this app knows how to sign in with.
 *
 * [Other] exists so an account linked to a provider added later (or linked from
 * another platform - Apple sign-in from an iOS build, say) does not crash the
 * mapping. Silently dropping an unknown provider would be worse: it would make
 * a linked account look unlinked.
 */
enum class AuthProvider {
    Google,
    Phone,
    Password,
    Anonymous,
    Other,
    ;

    internal companion object {
        /** Maps Firebase's `providerId` strings onto this enum. */
        fun fromProviderId(providerId: String): AuthProvider = when (providerId) {
            "google.com" -> Google
            "phone" -> Phone
            "password" -> Password
            "firebase" -> Anonymous
            else -> Other
        }
    }
}

