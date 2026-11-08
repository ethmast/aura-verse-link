package com.aura.verselink.credits

/**
 * Constants for the Aura Credits backend. None of these are secrets: the
 * backend URL is public, and the web client ID is the OAuth *audience* the
 * app asks Google for — it identifies the backend, it does not authenticate
 * anything by itself.
 */
object CreditsConfig {

    /** Deployed credits backend (Railway). */
    const val BASE_URL = "https://api-production-11e4.up.railway.app"

    /** OAuth 2.0 *web* client ID of the backend's Google Cloud project. Used
     *  as the serverClientId when requesting a Google ID token. */
    const val WEB_CLIENT_ID =
        "338199436748-f8lpqea1f5pid7d479ig8614ge5o6s71.apps.googleusercontent.com"

    /**
     * Play product IDs for credit packs, in display order. Must match the
     * backend's PRODUCT_CREDITS_JSON grant map. Products that don't exist in
     * Play Console yet are simply not returned by Play and are skipped.
     */
    val PRODUCT_IDS: List<String> = listOf("credits_099", "credits_499")

    /** The backend rejects format text longer than this (HTTP 400). */
    const val MAX_FORMAT_CHARS = 500
}
