package com.aura.verselink.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ResponseParsingTest {

    // --- OpenAI-compatible (Chat Completions) ---

    @Test
    fun `chat completion returns message content`() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"JHN.3.16"},"finish_reason":"stop"}]}"""
        assertEquals("JHN.3.16", OpenAICompatibleProvider.parseChatCompletion(body, "Test"))
    }

    @Test
    fun `chat completion supports content parts`() {
        val body = """{"choices":[{"message":{"content":[{"type":"text","text":"GEN.1.1"}]},"finish_reason":"stop"}]}"""
        assertEquals("GEN.1.1", OpenAICompatibleProvider.parseChatCompletion(body, "Test"))
    }

    @Test
    fun `chat completion empty content is an invalid response, not a crash`() {
        val body = """{"choices":[{"message":{"content":""},"finish_reason":"length"}]}"""
        try {
            OpenAICompatibleProvider.parseChatCompletion(body, "Test")
            fail("expected InvalidResponseError")
        } catch (e: AIError.InvalidResponseError) {
            assertTrue(e.message.contains("finish_reason=length"))
        }
    }

    @Test
    fun `chat completion error object inside 200 is an api error`() {
        val body = """{"error":{"message":"model not found","code":"404"}}"""
        try {
            OpenAICompatibleProvider.parseChatCompletion(body, "Test")
            fail("expected ApiError")
        } catch (e: AIError.ApiError) {
            assertTrue(e.message.contains("model not found"))
        }
    }

    @Test
    fun `temperature is withheld from reasoning models only`() {
        assertFalse(OpenAICompatibleProvider.supportsTemperature("openai/gpt-oss-20b"))
        assertFalse(OpenAICompatibleProvider.supportsTemperature("gpt-5-mini"))
        assertFalse(OpenAICompatibleProvider.supportsTemperature("deepseek-reasoner"))
        assertTrue(OpenAICompatibleProvider.supportsTemperature("gpt-4o-mini"))
        assertTrue(OpenAICompatibleProvider.supportsTemperature("llama-3.3-70b-versatile"))
    }

    // --- Google (Gemini generateContent) ---

    @Test
    fun `gemini returns last non-thought text part`() {
        val body = """{"candidates":[{"content":{"parts":[
            {"text":"thinking…","thought":true},
            {"text":"PSA.23.1"}
        ]},"finishReason":"STOP"}]}"""
        assertEquals("PSA.23.1", GoogleAIProvider.parseGenerateContent(body))
    }

    @Test
    fun `gemini blocked prompt is an invalid response`() {
        val body = """{"promptFeedback":{"blockReason":"SAFETY"}}"""
        try {
            GoogleAIProvider.parseGenerateContent(body)
            fail("expected InvalidResponseError")
        } catch (e: AIError.InvalidResponseError) {
            assertTrue(e.message.contains("SAFETY"))
        }
    }

    @Test
    fun `gemini missing candidates is an invalid response`() {
        try {
            GoogleAIProvider.parseGenerateContent("""{}""")
            fail("expected InvalidResponseError")
        } catch (e: AIError.InvalidResponseError) {
            // expected
        }
    }

    // --- Shared HTTP error mapping ---

    @Test
    fun `http errors map to typed errors with provider detail`() {
        val rateLimited = httpError(429, """{"error":{"message":"slow down"}}""", "Groq")
        assertTrue(rateLimited is AIError.RateLimitError)
        assertTrue(rateLimited.message!!.contains("slow down"))

        val auth = httpError(401, """{"error":{"message":"bad key"}}""", "OpenAI")
        assertTrue(auth is AIError.AuthError)

        val credits = httpError(402, "", "OpenRouter")
        assertTrue(credits is AIError.AuthError)

        val server = httpError(503, "", "DeepSeek")
        assertTrue(server is AIError.NetworkError)

        val badModel = httpError(404, """{"error":{"message":"unknown model"}}""", "Groq")
        assertTrue(badModel is AIError.ApiError)
        assertEquals(404, (badModel as AIError.ApiError).statusCode)
    }
}
