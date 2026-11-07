package com.aura.verselink.credits

/**
 * Error taxonomy for the credits backend, mirroring the status/error-code
 * contract in the backend's src/app.js. UI code switches on these types to
 * show the right message; raw HTTP details never reach the user.
 */
sealed class CreditsException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** 401 — no session, or the session JWT expired / was rejected. The user
     *  needs to sign in again. */
    class SessionExpired(message: String = "Session expired") : CreditsException(message)

    /** 402 — balance too low for a format call. */
    class InsufficientCredits(message: String = "Insufficient credits") : CreditsException(message)

    /** 429 — a daily spend cap (per-user or global) was hit. */
    class DailyCap(val global: Boolean, message: String = "Daily cap exceeded") :
        CreditsException(message)

    /** 503 — the backend is up but a feature (purchases / AI provider) is not
     *  configured on the server yet. */
    class NotConfigured(message: String = "Service not configured") : CreditsException(message)

    /** Transport failure or a 5xx the caller may retry (e.g. Railway waking). */
    class Network(message: String, cause: Throwable? = null) : CreditsException(message, cause)

    /** Any other non-2xx response. [errorCode] is the backend's `error` string. */
    class Api(val status: Int, val errorCode: String?, message: String) : CreditsException(message)

    /** Google Sign-In itself failed or was cancelled before any backend call. */
    class SignInFailed(message: String, cause: Throwable? = null) : CreditsException(message, cause)
}

/**
 * Maps an HTTP status + backend `error` code to a [CreditsException].
 * Pure function — kept separate from the HTTP client so it is unit-testable.
 */
fun creditsErrorFor(status: Int, errorCode: String?): CreditsException {
    val detail = if (errorCode.isNullOrBlank()) "" else " ($errorCode)"
    return when {
        status == 401 -> CreditsException.SessionExpired("Session expired or invalid$detail")
        status == 402 -> CreditsException.InsufficientCredits("Insufficient credits$detail")
        status == 429 -> CreditsException.DailyCap(
            global = errorCode == "global_daily_cap_exceeded",
            message = "Daily credits cap reached$detail"
        )
        status == 503 -> CreditsException.NotConfigured("Credits service not fully configured$detail")
        status in 500..599 -> CreditsException.Network("Credits service error (HTTP $status)$detail")
        else -> CreditsException.Api(status, errorCode, "Credits request failed (HTTP $status)$detail")
    }
}
