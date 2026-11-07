package com.aura.verselink.credits

import org.json.JSONObject
import java.util.Base64

/**
 * Reads claims out of JWTs the app legitimately holds.
 *
 * Two places need this:
 * - The backend's /auth/google response is `{sessionToken, balanceMicros,
 *   isNewUser}` — it does NOT return the user id. The id is the `sub` claim
 *   of the session JWT itself, and the app needs it because Play purchases
 *   must carry it as the obfuscated account id (the backend rejects a
 *   purchase whose obfuscated id doesn't match the caller's user id).
 * - /auth/google doesn't return the account email either, so the Settings
 *   screen takes it from the Google ID token the user just signed in with.
 *
 * This is display/routing data only — nothing here verifies signatures.
 * The backend verifies every token it is handed; a forged claim decoded
 * here could only ever cause a mismatch the backend then rejects.
 */
object JwtClaims {

    /** Decodes the payload segment of a JWT into a JSONObject, or null. */
    fun payload(token: String?): JSONObject? {
        if (token.isNullOrBlank()) return null
        val parts = token.split(".")
        if (parts.size != 3) return null
        return try {
            var segment = parts[1].replace('-', '+').replace('_', '/')
            val remainder = segment.length % 4
            if (remainder != 0) segment += "=".repeat(4 - remainder)
            val bytes = Base64.getDecoder().decode(segment)
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }

    /** The backend's numeric user id from a session token's `sub` claim. */
    fun userIdFromSessionToken(token: String?): Long? {
        val sub = payload(token)?.optString("sub")?.takeIf { it.isNotBlank() } ?: return null
        return sub.toLongOrNull()
    }

    /** The `email` claim of a Google ID token (display purposes only). */
    fun emailFromIdToken(token: String?): String? =
        payload(token)?.optString("email")?.takeIf { it.isNotBlank() }

    /** The `exp` claim (epoch seconds) of a token, or null when absent. */
    fun expiryEpochSeconds(token: String?): Long? {
        val payload = payload(token) ?: return null
        return if (payload.has("exp")) payload.optLong("exp") else null
    }

    /**
     * True when there is no usable session: token missing, undecodable,
     * lacking an expiry, or already expired at [nowEpochSeconds].
     */
    fun isSessionExpired(token: String?, nowEpochSeconds: Long): Boolean {
        val exp = expiryEpochSeconds(token) ?: return true
        return exp <= nowEpochSeconds
    }
}
