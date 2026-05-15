package com.aura.verselink.ai

import android.content.SharedPreferences
import okhttp3.OkHttpClient

object AIProviderFactory {

    const val PROVIDER_GOOGLE = "google"
    const val PROVIDER_OPENAI = "openai"
    const val PROVIDER_GROQ   = "groq"
    const val PROVIDER_OLLAMA = "ollama"

    fun fromPrefs(prefs: SharedPreferences, client: OkHttpClient = OkHttpClient()): AIProvider? {
        val providerId = prefs.getString("provider_id", PROVIDER_GOOGLE) ?: PROVIDER_GOOGLE
        val modelName  = prefs.getString("model_name", defaultModelFor(providerId))
            ?.takeIf { it.isNotEmpty() } ?: return null
        val apiKey     = prefs.getString(apiKeyPrefFor(providerId), "") ?: ""

        if (apiKey.isEmpty() && providerId != PROVIDER_OLLAMA) return null

        return when (providerId) {
            PROVIDER_GOOGLE -> GoogleAIProvider(apiKey, modelName, client)
            PROVIDER_OPENAI -> OpenAIProvider(apiKey, modelName, "https://api.openai.com", client)
            PROVIDER_GROQ   -> OpenAIProvider(apiKey, modelName, "https://api.groq.com/openai", client)
            PROVIDER_OLLAMA -> {
                val baseUrl = prefs.getString("ollama_base_url", "http://localhost:11434")
                    ?.takeIf { it.isNotEmpty() } ?: "http://localhost:11434"
                OpenAIProvider("", modelName, baseUrl, client)
            }
            else -> null
        }
    }

    fun apiKeyPrefFor(providerId: String): String = "api_key_$providerId"

    fun defaultModelFor(providerId: String): String = when (providerId) {
        PROVIDER_GOOGLE -> "gemma-4-31b-it"
        PROVIDER_OPENAI -> "gpt-4o-mini"
        PROVIDER_GROQ   -> "llama3-8b-8192"
        PROVIDER_OLLAMA -> "llama3"
        else            -> ""
    }
}
