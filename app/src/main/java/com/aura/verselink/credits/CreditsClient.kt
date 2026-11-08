package com.aura.verselink.credits

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * HTTP client for the Aura Credits backend (see the backend repo's
 * README/src/app.js for the contract this mirrors).
 *
 * The session JWT is attached as a Bearer token and is never logged; nor
 * are Google ID tokens or purchase tokens.
 */
class CreditsClient(
    private val store: CreditsStore,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = CreditsConfig.BASE_URL
) {

    /** Exchanges a Google ID token for a backend session; stores the session
     *  and returns the parsed result. */
    suspend fun signInWithGoogle(idToken: String): CreditsApi.AuthResult {
        val body = JSONObject().put("idToken", idToken).toString()
        val responseBody = execute(post("/auth/google", body, authenticated = false))
        val result = CreditsApi.parseAuthResponse(responseBody)
        store.saveSession(result.sessionToken, JwtClaims.emailFromIdToken(idToken), result.balanceMicros)
        return result
    }

    /** Current balance in microdollars; also refreshes the cached balance. */
    suspend fun fetchBalance(): Long {
        val body = execute(get("/balance"))
        val balance = CreditsApi.parseBalanceResponse(body)
        store.updateBalance(balance)
        return balance
    }

    /**
     * Formats one transcript snippet through the backend. The backend applies
     * the same extractor prompt the app uses in BYOK mode, so [text] is the
     * raw transcript, not a prompt. One retry on HTTP 502 / transport failure:
     * on Railway's free plan a sleeping service answers 502 while it wakes.
     */
    suspend fun format(text: String): CreditsApi.FormatResult {
        val clipped = text.takeLast(CreditsConfig.MAX_FORMAT_CHARS)
        val body = JSONObject().put("text", clipped).toString()
        var lastError: CreditsException? = null
        for (attempt in 0..1) {
            if (attempt > 0) delay(2_000)
            try {
                val responseBody = execute(post("/format", body, authenticated = true))
                val result = CreditsApi.parseFormatResponse(responseBody)
                store.updateBalance(result.balanceMicros)
                return result
            } catch (e: CreditsException.Network) {
                lastError = e // 502 wake-up or transport failure: retry once
            }
        }
        throw lastError ?: CreditsException.Network("Credits format call failed")
    }

    /** Asks the backend to verify a Play purchase and grant its credits. */
    suspend fun verifyPurchase(productId: String, purchaseToken: String): CreditsApi.VerifyResult {
        val body = JSONObject()
            .put("productId", productId)
            .put("purchaseToken", purchaseToken)
            .toString()
        val responseBody = execute(post("/purchase/verify", body, authenticated = true))
        val result = CreditsApi.parseVerifyResponse(responseBody)
        store.updateBalance(result.balanceMicros)
        return result
    }

    /** Deletes the account and all its backend data. The caller clears the
     *  local session afterwards. */
    suspend fun deleteAccount() {
        execute(post("/account/delete", "{}", authenticated = true))
    }

    /** Fire-and-forget wake-up ping for the sleeping backend. Never throws. */
    suspend fun pingHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/health").get().build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    // --- internals ---

    private fun post(path: String, jsonBody: String, authenticated: Boolean): Request {
        val builder = Request.Builder()
            .url("$baseUrl$path")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
        if (authenticated) builder.header("Authorization", "Bearer ${requireSessionToken()}")
        return builder.build()
    }

    private fun get(path: String): Request = Request.Builder()
        .url("$baseUrl$path")
        .header("Authorization", "Bearer ${requireSessionToken()}")
        .get()
        .build()

    private fun requireSessionToken(): String =
        store.sessionToken() ?: throw CreditsException.SessionExpired("Not signed in")

    /** Runs the request; returns the body on 2xx, throws a mapped
     *  [CreditsException] otherwise. */
    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw CreditsException.Network("Could not reach the credits service: ${e.message}", e)
        }
        response.use {
            val body = it.body?.string() ?: ""
            if (!it.isSuccessful) {
                throw creditsErrorFor(it.code, CreditsApi.parseErrorCode(body))
            }
            body
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
