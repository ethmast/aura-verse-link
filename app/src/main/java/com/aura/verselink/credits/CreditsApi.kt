package com.aura.verselink.credits

import org.json.JSONObject

/**
 * Response DTOs for the credits backend plus their JSON parsers.
 *
 * Parsers are pure functions over the raw body so they can be unit-tested
 * on the JVM with real response shapes. Field names match the backend's
 * src/app.js exactly; money is integer microdollars everywhere.
 */
object CreditsApi {

    data class AuthResult(
        val sessionToken: String,
        val balanceMicros: Long,
        val isNewUser: Boolean
    )

    data class FormatResult(
        val result: String,
        val balanceMicros: Long,
        val chargedMicros: Long
    )

    data class VerifyResult(
        /** True when this call granted new credits. */
        val granted: Boolean,
        /** True when the token had already been granted before (safe replay). */
        val alreadyGranted: Boolean,
        /** Credits granted by this call; 0 when [alreadyGranted]. */
        val grantedMicros: Long,
        val balanceMicros: Long
    )

    private fun parseJsonObject(body: String, what: String): JSONObject = try {
        JSONObject(body)
    } catch (e: Exception) {
        throw CreditsException.Api(-1, null, "Credits service returned an unreadable $what response")
    }

    private fun requiredLong(json: JSONObject, field: String, what: String): Long {
        if (!json.has(field)) {
            throw CreditsException.Api(-1, null, "Credits $what response is missing '$field'")
        }
        return json.optLong(field)
    }

    fun parseAuthResponse(body: String): AuthResult {
        val json = parseJsonObject(body, "sign-in")
        val token = json.optString("sessionToken").takeIf { it.isNotBlank() }
            ?: throw CreditsException.Api(-1, null, "Credits sign-in response is missing 'sessionToken'")
        return AuthResult(
            sessionToken = token,
            balanceMicros = requiredLong(json, "balanceMicros", "sign-in"),
            isNewUser = json.optBoolean("isNewUser", false)
        )
    }

    fun parseBalanceResponse(body: String): Long =
        requiredLong(parseJsonObject(body, "balance"), "balanceMicros", "balance")

    fun parseFormatResponse(body: String): FormatResult {
        val json = parseJsonObject(body, "format")
        val result = json.optString("result").takeIf { it.isNotBlank() }
            ?: throw CreditsException.Api(-1, null, "Credits format response is missing 'result'")
        return FormatResult(
            result = result,
            balanceMicros = requiredLong(json, "balanceMicros", "format"),
            chargedMicros = if (json.has("chargedMicros")) json.optLong("chargedMicros") else 0L
        )
    }

    fun parseVerifyResponse(body: String): VerifyResult {
        val json = parseJsonObject(body, "purchase verification")
        return VerifyResult(
            granted = json.optBoolean("granted", false),
            alreadyGranted = json.optBoolean("alreadyGranted", false),
            grantedMicros = if (json.has("grantedMicros")) json.optLong("grantedMicros") else 0L,
            balanceMicros = requiredLong(json, "balanceMicros", "purchase verification")
        )
    }

    /** The backend's `error` string from an error body, or null. */
    fun parseErrorCode(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            JSONObject(body).optString("error").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}
