package com.aura.verselink.ai

sealed class AIError : Exception() {
    /** The API key was rejected, is missing, or the account has no credit/quota. */
    data class AuthError(override val message: String) : AIError()

    /** HTTP 429 from the provider. */
    data class RateLimitError(override val message: String) : AIError()

    /** Transport failure or a retryable server error (HTTP 5xx). */
    data class NetworkError(override val message: String, override val cause: Throwable? = null) : AIError()

    /** Any other non-2xx response (unknown model, bad request, …). Not retryable. */
    data class ApiError(val statusCode: Int, override val message: String) : AIError()

    /** The provider answered 2xx but the body had no usable text (blocked, empty, or changed shape). */
    data class InvalidResponseError(override val message: String) : AIError()
}

interface AIProvider {
    suspend fun generateContent(prompt: String): String
}
