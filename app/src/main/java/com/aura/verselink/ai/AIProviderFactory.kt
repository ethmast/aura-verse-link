package com.aura.verselink.ai

import android.content.SharedPreferences
import okhttp3.OkHttpClient

/**
 * Builds an [AIProvider] from the user's saved settings.
 *
 * Preferences:
 * - `provider_id` — the selected provider (see [ProviderCatalog])
 * - `api_key_<providerId>` — one key per provider, so switching providers
 *   never loses another provider's key
 * - `model_name_<providerId>` — one model per provider; falls back to the
 *   legacy shared `model_name` written by older versions
 * - `base_url_<providerId>` — for providers that allow a custom base URL;
 *   falls back to the legacy `ollama_base_url`
 */
object AIProviderFactory {

    const val PROVIDER_GOOGLE = ProviderCatalog.GOOGLE
    const val PROVIDER_OPENAI = ProviderCatalog.OPENAI
    const val PROVIDER_GROQ = ProviderCatalog.GROQ
    const val PROVIDER_OPENROUTER = ProviderCatalog.OPENROUTER
    const val PROVIDER_DEEPSEEK = ProviderCatalog.DEEPSEEK
    const val PROVIDER_CEREBRAS = ProviderCatalog.CEREBRAS
    const val PROVIDER_OLLAMA = ProviderCatalog.OLLAMA
    const val PROVIDER_CUSTOM = ProviderCatalog.CUSTOM

    fun fromPrefs(prefs: SharedPreferences, client: OkHttpClient = OkHttpClient()): AIProvider? {
        val providerId = prefs.getString("provider_id", PROVIDER_GOOGLE) ?: PROVIDER_GOOGLE
        val definition = ProviderCatalog.get(providerId) ?: return null

        val modelName = savedModel(prefs, providerId) ?: return null
        val apiKey = prefs.getString(apiKeyPrefFor(providerId), "") ?: ""

        if (definition.requiresApiKey && apiKey.isEmpty()) return null

        return if (providerId == PROVIDER_GOOGLE) {
            GoogleAIProvider(apiKey, modelName, client)
        } else {
            val baseUrl = resolvedBaseUrl(prefs, definition) ?: return null
            OpenAICompatibleProvider(
                providerName = definition.displayName,
                apiKey = apiKey,
                modelName = modelName,
                baseUrl = baseUrl,
                client = client,
                extraHeaders = definition.extraHeaders,
                useMaxCompletionTokens = definition.useMaxCompletionTokens
            )
        }
    }

    /**
     * The model saved for [providerId]: its per-provider pref, else the
     * legacy shared pref (only when the legacy pref belongs to the currently
     * selected provider, which `provider_id` tells us), else the catalog default.
     */
    fun savedModel(prefs: SharedPreferences, providerId: String): String? {
        prefs.getString(modelPrefFor(providerId), null)?.takeIf { it.isNotEmpty() }?.let { return it }
        val selectedProvider = prefs.getString("provider_id", PROVIDER_GOOGLE) ?: PROVIDER_GOOGLE
        if (selectedProvider == providerId) {
            prefs.getString("model_name", null)?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return ProviderCatalog.defaultModelFor(providerId).takeIf { it.isNotEmpty() }
    }

    /**
     * The effective base URL for an OpenAI-compatible provider: the user's
     * override where allowed (normalized and validated), else the catalog
     * default. Null when there is no valid URL — e.g. the Custom provider
     * before a URL has been entered, or an http:// URL pointing off the LAN.
     */
    fun resolvedBaseUrl(prefs: SharedPreferences, definition: ProviderCatalog.Provider): String? {
        if (definition.allowsCustomBaseUrl) {
            val saved = prefs.getString(baseUrlPrefFor(definition.id), null)
                ?: prefs.getString("base_url", null)
                ?: if (definition.id == PROVIDER_OLLAMA) prefs.getString("ollama_base_url", null) else null
            if (!saved.isNullOrEmpty()) {
                return UrlRules.normalizeBaseUrl(saved)
            }
            if (definition.id == PROVIDER_CUSTOM) return null
        }
        return definition.baseUrl?.let { UrlRules.normalizeBaseUrl(it) ?: it.trimEnd('/') }
    }

    /** True when the saved settings are complete enough to run the service. */
    fun isConfigured(prefs: SharedPreferences): Boolean = fromPrefs(prefs) != null

    fun apiKeyPrefFor(providerId: String): String = "api_key_$providerId"
    fun modelPrefFor(providerId: String): String = "model_name_$providerId"
    fun baseUrlPrefFor(providerId: String): String = "base_url_$providerId"

    fun defaultModelFor(providerId: String): String = ProviderCatalog.defaultModelFor(providerId)
}
