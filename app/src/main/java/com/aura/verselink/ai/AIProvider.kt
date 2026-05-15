package com.aura.verselink.ai

sealed class AIError : Exception() {
    data class AuthError(override val message: String) : AIError()
    data class RateLimitError(override val message: String) : AIError()
    data class NetworkError(override val message: String, override val cause: Throwable? = null) : AIError()
}

interface AIProvider {
    suspend fun generateContent(prompt: String): String
}
