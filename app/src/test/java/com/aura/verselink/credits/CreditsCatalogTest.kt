package com.aura.verselink.credits

import com.aura.verselink.ai.ProviderCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditsCatalogTest {

    @Test
    fun `aura credits is in the catalog as a keyless account source`() {
        val credits = ProviderCatalog.get(ProviderCatalog.CREDITS)
        assertNotNull(credits)
        assertTrue(credits!!.isCredits)
        assertFalse(credits.requiresApiKey)
        assertEquals("Aura Credits", credits.displayName)
    }

    @Test
    fun `only the credits entry is flagged as credits`() {
        val flagged = ProviderCatalog.providers.filter { it.isCredits }
        assertEquals(listOf(ProviderCatalog.CREDITS), flagged.map { it.id })
    }

    @Test
    fun `product ids match the backend grant map`() {
        // The backend's PRODUCT_CREDITS_JSON is the only source of grant
        // amounts; credits_099 is the pack that exists there today. The app
        // may also ask Play about upcoming packs (credits_499) and must
        // tolerate their absence until they are created in Play Console.
        assertTrue(CreditsConfig.PRODUCT_IDS.contains("credits_099"))
        assertTrue(CreditsConfig.PRODUCT_IDS.contains("credits_499"))
    }

    @Test
    fun `backend coordinates are the deployed ones`() {
        assertEquals("https://api-production-11e4.up.railway.app", CreditsConfig.BASE_URL)
        assertTrue(CreditsConfig.WEB_CLIENT_ID.endsWith(".apps.googleusercontent.com"))
    }
}
