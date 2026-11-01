package com.aura.verselink.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * One client for every OpenAI-compatible Chat Completions API: OpenAI,
 * Groq, OpenRouter, DeepSeek, Cerebras, Ollama, and any custom endpoint.
 *
 * [baseUrl] must already be normalized (see [UrlRules.normalizeBaseUrl] and
 * [ProviderCatalog]); requests go to `$baseUrl/chat/completions`.
 */
class OpenAICompatibleProvider(
    private val providerName: String,
    private val apiKey: String,
    private val modelName: String,
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val extraHeaders: Map<String, String> = emptyMap(),
    private val useMaxCompletionTokens: Boolean = false
) : AIProvider {

    override suspend fun generateContent(prompt: String): String =
        retryOnNetworkError { attempt(prompt) }

    private suspend fun attempt(prompt: String): String = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject().apply {
            put("model", modelName)
            // 64 tokens was too few: reasoning models (GPT-OSS, DeepSeek
            // Reasoner, …) spend tokens thinking before the answer starts,
            // and an empty completion looks like a failure. 512 is still
            // a fraction of a cent and the extractor wants a one-line reply.
            put(if (useMaxCompletionTokens) "max_completion_tokens" else "max_tokens", 512)
            if (supportsTemperature(modelName)) put("temperature", 0.0)
            put("messages", JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }
            ))
        }

        val requestBuilder = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))

        if (apiKey.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }
        extraHeaders.forEach { (name, value) -> requestBuilder.header(name, value) }

        // Never log the key, the prompt, or response bodies here — only that
        // a call happened. Response text is surfaced through the in-app log
        // by the caller, which the user controls.
        Log.d(TAG, "POST $baseUrl/chat/completions model=$modelName")

        val response = try {
            client.newCall(requestBuilder.build()).execute()
        } catch (e: IOException) {
            throw AIError.NetworkError("Could not reach $providerName: ${e.message}", e)
        }

        response.use {
            val body = it.body?.string() ?: ""
            if (!it.isSuccessful) throw httpError(it.code, body, providerName)
            parseChatCompletion(body, providerName)
        }
    }

    companion object {
        private const val TAG = "OpenAICompatible"

        /**
         * Reasoning-style models reject a custom temperature (and some reject
         * it silently into weird behaviour), so we only send temperature=0
         * to models known to accept it.
         */
        internal fun supportsTemperature(modelName: String): Boolean {
            val m = modelName.lowercase()
            return !(m.startsWith("gpt-5") || m.startsWith("o1") || m.startsWith("o3") ||
                m.startsWith("o4") || m.contains("reasoner") || m.contains("gpt-oss"))
        }

        /** Extracts the assistant text from a Chat Completions response body. */
        fun parseChatCompletion(body: String, providerName: String = "AI provider"): String {
            val json = try {
                JSONObject(body)
            } catch (e: Exception) {
                throw AIError.InvalidResponseError("$providerName returned an unreadable response")
            }
            // Some servers report errors with HTTP 200 and an error object.
            json.optJSONObject("error")?.let { error ->
                val msg = error.optString("message").ifBlank { "unknown error" }
                throw AIError.ApiError(200, "$providerName error: $msg")
            }
            val choices = json.optJSONArray("choices")
                ?: throw AIError.InvalidResponseError("$providerName response had no choices")
            if (choices.length() == 0) {
                throw AIError.InvalidResponseError("$providerName response had no choices")
            }
            val message = choices.getJSONObject(0).optJSONObject("message")
                ?: throw AIError.InvalidResponseError("$providerName response had no message")
            val content = when (val raw = message.opt("content")) {
                is String -> raw
                is JSONArray -> buildString {
                    // Content-parts shape used by some compatible servers.
                    for (i in 0 until raw.length()) {
                        val part = raw.optJSONObject(i) ?: continue
                        if (part.optString("type") == "text") append(part.optString("text"))
                    }
                }
                else -> ""
            }
            if (content.isBlank()) {
                val finish = choices.getJSONObject(0).optString("finish_reason", "unknown")
                throw AIError.InvalidResponseError(
                    "$providerName returned an empty reply (finish_reason=$finish). " +
                        "The model may be a reasoning model that needs a larger token budget."
                )
            }
            return content
        }
    }
}
