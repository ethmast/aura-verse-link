package com.aura.verselink.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCatalogTest {

    @Test
    fun `provider ids are unique`() {
        val ids = ProviderCatalog.providers.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every provider default model is one of its presets`() {
        for (provider in ProviderCatalog.providers) {
            if (provider.models.isEmpty()) continue // Custom provider: free-text model only
            assertTrue(
                "${provider.id} default '${provider.defaultModel}' is not in its preset list",
                provider.models.any { it.id == provider.defaultModel }
            )
        }
    }

    @Test
    fun `openai-compatible providers have an https or local base url`() {
        for (provider in ProviderCatalog.providers) {
            val url = provider.baseUrl ?: continue
            assertTrue("${provider.id} base url not allowed: $url", UrlRules.isUrlAllowed(url))
        }
    }

    @Test
    fun `openrouter is present and speaks the shared format`() {
        val openRouter = ProviderCatalog.get(ProviderCatalog.OPENROUTER)
        assertTrue(openRouter != null)
        assertEquals("https://openrouter.ai/api/v1", openRouter!!.baseUrl)
        assertTrue(openRouter.requiresApiKey)
        assertTrue(openRouter.extraHeaders.containsKey("X-Title"))
    }

    @Test
    fun `retired models are not offered as defaults`() {
        // llama3-8b-8192 was the old factory default; Groq removed it.
        assertFalse(
            ProviderCatalog.providers.any { it.defaultModel == "llama3-8b-8192" }
        )
        // gemini-2.0-flash was retired by Google.
        assertFalse(
            ProviderCatalog.providers.any { p -> p.models.any { it.id == "gemini-2.0-flash" } }
        )
    }
}
