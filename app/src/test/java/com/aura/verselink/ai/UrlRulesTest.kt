package com.aura.verselink.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRulesTest {

    @Test
    fun `bare host gets v1 appended`() {
        assertEquals("http://localhost:11434/v1", UrlRules.normalizeBaseUrl("http://localhost:11434"))
        assertEquals("http://192.168.1.10:11434/v1", UrlRules.normalizeBaseUrl("http://192.168.1.10:11434/"))
        assertEquals("https://api.example.com/v1", UrlRules.normalizeBaseUrl("https://api.example.com"))
    }

    @Test
    fun `existing path is left alone`() {
        assertEquals("https://api.groq.com/openai/v1", UrlRules.normalizeBaseUrl("https://api.groq.com/openai/v1"))
        assertEquals("https://openrouter.ai/api/v1", UrlRules.normalizeBaseUrl("https://openrouter.ai/api/v1/"))
    }

    @Test
    fun `pasted chat completions suffix is stripped`() {
        assertEquals(
            "https://api.openai.com/v1",
            UrlRules.normalizeBaseUrl("https://api.openai.com/v1/chat/completions")
        )
    }

    @Test
    fun `remote http is rejected, remote https accepted`() {
        assertNull(UrlRules.normalizeBaseUrl("http://api.example.com/v1"))
        assertEquals("https://api.example.com/v1", UrlRules.normalizeBaseUrl("https://api.example.com/v1"))
    }

    @Test
    fun `private and loopback hosts count as local`() {
        assertTrue(UrlRules.isLocalHost("localhost"))
        assertTrue(UrlRules.isLocalHost("127.0.0.1"))
        assertTrue(UrlRules.isLocalHost("10.0.0.5"))
        assertTrue(UrlRules.isLocalHost("172.16.0.9"))
        assertTrue(UrlRules.isLocalHost("192.168.1.20"))
        assertTrue(UrlRules.isLocalHost("my-pc.local"))
        assertFalse(UrlRules.isLocalHost("172.32.0.9"))
        assertFalse(UrlRules.isLocalHost("8.8.8.8"))
        assertFalse(UrlRules.isLocalHost("example.com"))
    }

    @Test
    fun `garbage is rejected`() {
        assertNull(UrlRules.normalizeBaseUrl(""))
        assertNull(UrlRules.normalizeBaseUrl(null))
        assertNull(UrlRules.normalizeBaseUrl("not a url"))
        assertNull(UrlRules.normalizeBaseUrl("ftp://localhost:11434"))
    }
}
