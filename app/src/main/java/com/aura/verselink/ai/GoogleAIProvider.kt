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
 * Google's native Gemini API (generativelanguage.googleapis.com).
 *
 * The API key travels in the `x-goog-api-key` header — Google's current
 * guidance — never in the URL, where it would leak into logs. Nothing in
 * this class logs the key, the prompt, or response bodies.
 */
class GoogleAIProvider(
    private val apiKey: String,
    private val modelName: String,
    private val client: OkHttpClient = OkHttpClient()
) : AIProvider {

    override suspend fun generateContent(prompt: String): String =
        retryOnNetworkError { attempt(prompt) }

    private suspend fun attempt(prompt: String): String = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"

        val bodyJson = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.0)
                put("maxOutputTokens", 512)
            })
        }

        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        Log.d(TAG, "POST generateContent model=$modelName")

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw AIError.NetworkError("Could not reach Google AI: ${e.message}", e)
        }

        response.use {
            val body = it.body?.string() ?: ""
            if (!it.isSuccessful) throw httpError(it.code, body, "Google AI")
            parseGenerateContent(body)
        }
    }

    companion object {
        private const val TAG = "GoogleAIProvider"

        /** Extracts the reply text from a Gemini generateContent response body. */
        fun parseGenerateContent(body: String): String {
            val json = try {
                JSONObject(body)
            } catch (e: Exception) {
                throw AIError.InvalidResponseError("Google AI returned an unreadable response")
            }
            val blockReason = json.optJSONObject("promptFeedback")?.optString("blockReason", "")
            if (!blockReason.isNullOrEmpty()) {
                throw AIError.InvalidResponseError("Prompt blocked by Google AI: $blockReason")
            }
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                throw AIError.InvalidResponseError("Google AI response had no candidates")
            }
            val candidate = candidates.getJSONObject(0)
            candidate.optString("finishReason", "").takeIf { it == "SAFETY" || it == "RECITATION" }?.let {
                throw AIError.InvalidResponseError("Google AI stopped the reply (finishReason=$it)")
            }
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                ?: throw AIError.InvalidResponseError("Google AI response had no content")
            // Skip thought parts (thinking models); take the last real text part.
            var result = ""
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (!part.optBoolean("thought", false)) {
                    part.optString("text", "").takeIf { it.isNotEmpty() }?.let { result = it }
                }
            }
            if (result.isEmpty()) {
                throw AIError.InvalidResponseError("Google AI returned an empty reply")
            }
            return result
        }
    }
}
