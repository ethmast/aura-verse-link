package com.aura.verselink.ai

import kotlinx.coroutines.delay

/**
 * Runs [block], retrying only [AIError.NetworkError] (transport failures and
 * HTTP 5xx) with the same backoff the Google provider historically used:
 * attempts at 0s, +1s, +2s. Auth, rate-limit, and other API errors fail fast
 * so the user gets a useful message instead of a silent retry loop.
 */
suspend fun <T> retryOnNetworkError(block: suspend () -> T): T {
    var lastError: AIError.NetworkError = AIError.NetworkError("No attempts made")
    for (delayMs in longArrayOf(0, 1000, 2000)) {
        if (delayMs > 0) delay(delayMs)
        try {
            return block()
        } catch (e: AIError.NetworkError) {
            lastError = e
        }
    }
    throw lastError
}
