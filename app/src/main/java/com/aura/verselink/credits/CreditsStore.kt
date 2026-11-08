package com.aura.verselink.credits

import android.content.SharedPreferences

/**
 * Persists the credits session in the app's existing SharedPreferences.
 * That file is already excluded from Android cloud backup and
 * device-to-device transfer (see res/xml/backup_rules.xml and
 * data_extraction_rules.xml, which exclude the whole sharedpref domain),
 * so the session JWT gets exactly the protection API keys already have:
 * on a new device the user simply signs in again.
 *
 * The token itself is never logged anywhere.
 */
class CreditsStore(private val prefs: SharedPreferences) {

    fun saveSession(sessionToken: String, email: String?, balanceMicros: Long) {
        prefs.edit()
            .putString(KEY_SESSION_TOKEN, sessionToken)
            // Cache the user id (decoded from the token's `sub` claim) so the
            // billing flow doesn't have to re-decode on every purchase.
            .putLong(KEY_USER_ID, JwtClaims.userIdFromSessionToken(sessionToken) ?: -1L)
            .putString(KEY_EMAIL, email)
            .putLong(KEY_BALANCE_MICROS, balanceMicros)
            .apply()
    }

    fun sessionToken(): String? = prefs.getString(KEY_SESSION_TOKEN, null)?.takeIf { it.isNotBlank() }

    /** Backend numeric user id, or null when signed out / unknown. Needed as
     *  the Play obfuscated account id for purchases. */
    fun userId(): Long? = prefs.getLong(KEY_USER_ID, -1L).takeIf { it > 0 }

    fun email(): String? = prefs.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() }

    fun cachedBalanceMicros(): Long = prefs.getLong(KEY_BALANCE_MICROS, 0L)

    fun updateBalance(balanceMicros: Long) {
        prefs.edit().putLong(KEY_BALANCE_MICROS, balanceMicros).apply()
    }

    /** True when a session token exists and has not reached its expiry. */
    fun hasValidSession(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Boolean =
        !JwtClaims.isSessionExpired(sessionToken(), nowEpochSeconds)

    /** Drops the session and all cached credits state (sign-out, expiry,
     *  account deletion). */
    fun clear() {
        prefs.edit()
            .remove(KEY_SESSION_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .remove(KEY_BALANCE_MICROS)
            .apply()
    }

    companion object {
        private const val KEY_SESSION_TOKEN = "credits_session_token"
        private const val KEY_USER_ID = "credits_user_id"
        private const val KEY_EMAIL = "credits_email"
        private const val KEY_BALANCE_MICROS = "credits_balance_micros"
    }
}
