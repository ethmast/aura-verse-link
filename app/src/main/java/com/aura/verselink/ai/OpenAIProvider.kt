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

class OpenAIProvider(
    private val apiKey: String,
    private val modelName: String,
    private val baseUrl: String = "https://api.openai.com",
    private val client: OkHttpClient = OkHttpClient()
) : AIProvider {

    override suspend fun generateContent(prompt: String): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl/v1/chat/completions"

        val bodyJson = JSONObject().apply {
            put("model", modelName)
            put("max_tokens", 64)
            put("temperature", 0.0)
            put("messages", JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }
            ))
        }

        val keyHint = if (apiKey.length > 8) "${apiKey.take(8)}…" else "(empty)"
        Log.d("OpenAIProvider", "POST $url  model=$modelName  key=$keyHint")

        val requestBuilder = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))

        if (apiKey.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        val body = response.body?.string() ?: ""
        Log.d("OpenAIProvider", "HTTP ${response.code}: $body")

        when (response.code) {
            200 -> JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            401, 403 -> throw AIError.AuthError("Invalid API key (HTTP ${response.code}): $body")
            429 -> throw AIError.RateLimitError("Rate limit hit (HTTP 429)")
            else -> throw AIError.NetworkError("HTTP ${response.code}: $body")
        }
    }
}
