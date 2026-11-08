package com.aura.verselink.credits

/**
 * Decides the feedback toast shown after a successful Google sign-in.
 *
 * The starter-credit announcement must key off the balance the backend
 * actually credited, never off [CreditsApi.AuthResult.isNewUser] alone:
 * the anti-abuse tombstone lets the backend create a fresh account with
 * NO grant (balance 0) when a previously deleted account signs back in,
 * and announcing "credits were added" there would be wrong.
 */
object SignInFeedback {

    /**
     * The starter-grant announcement for a sign-in result, or null when no
     * grant landed — a returning user, or a tombstoned re-creation whose
     * new account starts at 0.
     */
    fun starterGrantMessage(isNewUser: Boolean, balanceMicros: Long): String? {
        if (!isNewUser || balanceMicros <= 0) return null
        return "${CreditsFormat.formatCredits(balanceMicros)} were added to your account"
    }
}
