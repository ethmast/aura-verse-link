package com.aura.verselink.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GoogleAIProvider(
    private val apiKey: String,
    private val modelName: String,
    private val client: OkHttpClient = OkHttpClient()
) : AIProvider {

    override suspend fun generateContent(prompt: String): String {
        var lastError: AIError = AIError.NetworkError("No attempts made")
        val delaysMs = longArrayOf(0, 1000, 2000)
        for (delayMs in delaysMs) {
            if (delayMs > 0) delay(delayMs)
            try {
                return attempt(prompt)
            } catch (e: AIError.NetworkError) {
                Log.w("GoogleAIProvider", "Retrying after: ${e.message}")
                lastError = e
            }
        }
        throw lastError
    }

    private suspend fun attempt(prompt: String): String = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"

        val bodyJson = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""

        when (response.code) {
            200 -> {
                val json = JSONObject(body)
                val blockReason = json.optJSONObject("promptFeedback")?.optString("blockReason", "")
                if (!blockReason.isNullOrEmpty()) {
                    throw AIError.NetworkError("Prompt blocked: $blockReason")
                }
                val parts = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                // Skip thought parts; return the last non-thought text
                var result = ""
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (!part.optBoolean("thought", false)) {
                        result = part.optString("text", "")
                    }
                }
                result
            }
            401, 403 -> throw AIError.AuthError("Invalid API key (HTTP ${response.code})")
            429 -> throw AIError.RateLimitError("Rate limit hit (HTTP 429)")
            else -> throw AIError.NetworkError("HTTP ${response.code}: $body")
        }
    }
}
