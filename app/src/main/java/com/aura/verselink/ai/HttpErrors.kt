package com.aura.verselink.ai

import org.json.JSONObject

/**
 * Extracts the human-readable error message from a provider error body.
 * Both the OpenAI shape (`{"error": {"message": …}}`) and the Gemini shape
 * (`{"error": {"message": …}}` — the same) are covered, plus a plain-string
 * `error` field some compatible servers return. Falls back to the raw body.
 */
internal fun extractErrorMessage(body: String): String {
    if (body.isBlank()) return ""
    return try {
        val error = JSONObject(body).opt("error")
        when (error) {
            is JSONObject -> error.optString("message").ifBlank { error.optString("code") }
            is String -> error
            else -> ""
        }.ifBlank { body.take(300) }
    } catch (e: Exception) {
        body.take(300)
    }
}

/** Maps an HTTP status + body to the [AIError] both providers share. */
internal fun httpError(statusCode: Int, body: String, providerName: String): AIError {
    val detail = extractErrorMessage(body)
    val suffix = if (detail.isNotBlank()) ": $detail" else ""
    return when {
        statusCode == 401 || statusCode == 403 ->
            AIError.AuthError("$providerName rejected the API key (HTTP $statusCode)$suffix")
        statusCode == 402 ->
            AIError.AuthError("$providerName reports no credit/quota on this account (HTTP 402)$suffix")
        statusCode == 429 ->
            AIError.RateLimitError("Rate limit hit on $providerName (HTTP 429)$suffix")
        statusCode in 500..599 ->
            AIError.NetworkError("$providerName server error (HTTP $statusCode)$suffix")
        else ->
            AIError.ApiError(statusCode, "$providerName error (HTTP $statusCode)$suffix")
    }
}
