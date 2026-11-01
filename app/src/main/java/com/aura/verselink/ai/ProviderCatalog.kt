package com.aura.verselink.ai

/**
 * Single source of truth for every supported AI provider.
 *
 * Almost every modern provider speaks the OpenAI Chat Completions format —
 * they differ only in base URL, API key, and model names — so they all share
 * [OpenAICompatibleProvider]. Google is the one exception: it keeps its
 * native Gemini API in [GoogleAIProvider]. Anything else OpenAI-compatible
 * (Together, Mistral, xAI, Fireworks, LM Studio, a LiteLLM proxy, …) works
 * through the [CUSTOM] provider with a user-supplied base URL.
 *
 * Model lists are presets, not a hard limit: Settings always offers
 * "Custom…" so a provider retiring or renaming a model never bricks the app.
 * This catalog is also what Settings renders, so the two can never drift apart.
 */
object ProviderCatalog {

    const val GOOGLE = "google"
    const val OPENAI = "openai"
    const val GROQ = "groq"
    const val OPENROUTER = "openrouter"
    const val DEEPSEEK = "deepseek"
    const val CEREBRAS = "cerebras"
    const val OLLAMA = "ollama"
    const val CUSTOM = "custom"

    /** Sentinel model id for the "Custom…" spinner entry. */
    const val CUSTOM_MODEL = "__custom__"

    data class ModelOption(val id: String, val label: String)

    data class Provider(
        val id: String,
        val displayName: String,
        /** Default base URL for OpenAI-compatible providers (no trailing slash). Null for Google (native API). */
        val baseUrl: String?,
        val requiresApiKey: Boolean,
        /** Whether the user may override the base URL (local servers, custom endpoints). */
        val allowsCustomBaseUrl: Boolean,
        val defaultModel: String,
        val models: List<ModelOption>,
        val apiKeyLabel: String,
        val hint: String,
        /** Extra headers sent with every request (e.g. OpenRouter app attribution). */
        val extraHeaders: Map<String, String> = emptyMap(),
        /** OpenAI's newer models reject `max_tokens` in favour of `max_completion_tokens`. */
        val useMaxCompletionTokens: Boolean = false
    )

    val providers: List<Provider> = listOf(
        Provider(
            id = GOOGLE,
            displayName = "Google AI Studio",
            baseUrl = null,
            requiresApiKey = true,
            allowsCustomBaseUrl = false,
            defaultModel = "gemma-4-26b-a4b-it",
            models = listOf(
                ModelOption("gemma-4-26b-a4b-it", "Gemma 4 26B (Recommended)"),
                ModelOption("gemma-4-31b-it", "Gemma 4 31B"),
                ModelOption("gemini-2.5-flash", "Gemini 2.5 Flash"),
                ModelOption("gemini-flash-lite-latest", "Gemini Flash-Lite (latest)")
            ),
            apiKeyLabel = "Google AI Studio Key",
            hint = "Get a free key at aistudio.google.com. Note: Google's free tier is often " +
                "rate-limited under load — if requests keep failing, try Groq or OpenRouter."
        ),
        Provider(
            id = OPENAI,
            displayName = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            requiresApiKey = true,
            allowsCustomBaseUrl = false,
            defaultModel = "gpt-4o-mini",
            models = listOf(
                ModelOption("gpt-4o-mini", "GPT-4o mini (Recommended)"),
                ModelOption("gpt-5-mini", "GPT-5 mini"),
                ModelOption("gpt-5-nano", "GPT-5 nano"),
                ModelOption("gpt-4o", "GPT-4o")
            ),
            apiKeyLabel = "OpenAI API Key",
            hint = "Get a key at platform.openai.com. Usage is pay-as-you-go; the mini/nano " +
                "models cost a fraction of a cent per detected reference.",
            useMaxCompletionTokens = true
        ),
        Provider(
            id = GROQ,
            displayName = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            requiresApiKey = true,
            allowsCustomBaseUrl = false,
            defaultModel = "openai/gpt-oss-20b",
            models = listOf(
                ModelOption("openai/gpt-oss-20b", "GPT-OSS 20B (Recommended, free tier)"),
                ModelOption("openai/gpt-oss-120b", "GPT-OSS 120B"),
                ModelOption("llama-3.3-70b-versatile", "Llama 3.3 70B"),
                ModelOption("llama-3.1-8b-instant", "Llama 3.1 8B Instant")
            ),
            apiKeyLabel = "Groq API Key",
            hint = "Get a free key at console.groq.com. Very fast, and the free tier is " +
                "generous. Groq rotates its model catalog often — if a preset disappears, " +
                "use Custom… with any model id from console.groq.com/docs/models."
        ),
        Provider(
            id = OPENROUTER,
            displayName = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            requiresApiKey = true,
            allowsCustomBaseUrl = false,
            defaultModel = "openrouter/free",
            models = listOf(
                ModelOption("openrouter/free", "Free models router (Recommended)"),
                ModelOption("openrouter/auto", "Auto router (best available, paid)"),
                ModelOption("meta-llama/llama-3.3-70b-instruct", "Llama 3.3 70B Instruct"),
                ModelOption("openai/gpt-4o-mini", "GPT-4o mini (via OpenRouter)")
            ),
            apiKeyLabel = "OpenRouter API Key",
            hint = "Get a key at openrouter.ai/keys. One key reaches hundreds of models " +
                "from many providers. Model ids look like \"provider/model\" — browse " +
                "openrouter.ai/models and use Custom… for anything not listed. Free " +
                "models need no credit balance.",
            extraHeaders = mapOf(
                "HTTP-Referer" to "https://github.com/ethmast/aura-verse-link",
                "X-Title" to "Aura Verse Link"
            )
        ),
        Provider(
            id = DEEPSEEK,
            displayName = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            requiresApiKey = true,
            allowsCustomBaseUrl = false,
            defaultModel = "deepseek-chat",
            models = listOf(
                ModelOption("deepseek-chat", "DeepSeek Chat (Recommended)"),
                ModelOption("deepseek-reasoner", "DeepSeek Reasoner")
            ),
            apiKeyLabel = "DeepSeek API Key",
            hint = "Get a key at platform.deepseek.com. Pay-as-you-go and inexpensive."
        ),
        Provider(
            id = CEREBRAS,
            displayName = "Cerebras",
            baseUrl = "https://api.cerebras.ai/v1",
            requiresApiKey = true,
            allowsCustomBaseUrl = false,
            defaultModel = "gpt-oss-120b",
            models = listOf(
                ModelOption("gpt-oss-120b", "GPT-OSS 120B (Recommended)"),
                ModelOption("llama-3.3-70b", "Llama 3.3 70B"),
                ModelOption("llama3.1-8b", "Llama 3.1 8B")
            ),
            apiKeyLabel = "Cerebras API Key",
            hint = "Get a key at cloud.cerebras.ai. Extremely fast inference with a free tier."
        ),
        Provider(
            id = OLLAMA,
            displayName = "Ollama (local)",
            baseUrl = "http://localhost:11434/v1",
            requiresApiKey = false,
            allowsCustomBaseUrl = true,
            defaultModel = "llama3.1",
            models = listOf(
                ModelOption("llama3.1", "llama3.1 (Recommended)"),
                ModelOption("qwen2.5", "qwen2.5"),
                ModelOption("mistral", "mistral")
            ),
            apiKeyLabel = "API Key (not required)",
            hint = "Runs models on your own hardware — nothing leaves your network. Enter the " +
                "device-reachable base URL above: for a PC on your Wi-Fi use its LAN address, " +
                "e.g. http://192.168.1.10:11434 (and start Ollama with OLLAMA_HOST=0.0.0.0). " +
                "The model must already be pulled (ollama pull llama3.1)."
        ),
        Provider(
            id = CUSTOM,
            displayName = "Custom (OpenAI-compatible)",
            baseUrl = null,
            requiresApiKey = false,
            allowsCustomBaseUrl = true,
            defaultModel = "",
            models = emptyList(),
            apiKeyLabel = "API Key (if the server needs one)",
            hint = "Any server that speaks the OpenAI Chat Completions format. Enter its base " +
                "URL above (a bare host gets /v1 appended) and the exact model id it serves. " +
                "Examples: Together and Mistral (https://api.together.xyz/v1, " +
                "https://api.mistral.ai/v1), xAI (https://api.x.ai/v1), LM Studio " +
                "(http://<pc-ip>:1234), llama.cpp server, or a LiteLLM proxy. Remote servers " +
                "must use https; http is only allowed for local/LAN addresses."
        )
    )

    private val byId: Map<String, Provider> = providers.associateBy { it.id }

    fun get(id: String?): Provider? = byId[id]

    fun defaultModelFor(providerId: String): String = byId[providerId]?.defaultModel ?: ""
}
