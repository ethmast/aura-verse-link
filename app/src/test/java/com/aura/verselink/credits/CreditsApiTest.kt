package com.aura.verselink.credits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CreditsApiTest {

    @Test
    fun `parses a sign-in response`() {
        val result = CreditsApi.parseAuthResponse(
            """{"sessionToken":"tok123","balanceMicros":100000,"isNewUser":true}"""
        )
        assertEquals("tok123", result.sessionToken)
        assertEquals(100000L, result.balanceMicros)
        assertTrue(result.isNewUser)
    }

    @Test
    fun `parses a balance response`() {
        assertEquals(250000L, CreditsApi.parseBalanceResponse("""{"balanceMicros":250000}"""))
    }

    @Test
    fun `parses a format response`() {
        val result = CreditsApi.parseFormatResponse(
            """{"result":"JHN.3.16","balanceMicros":99913,"chargedMicros":87}"""
        )
        assertEquals("JHN.3.16", result.result)
        assertEquals(99913L, result.balanceMicros)
        assertEquals(87L, result.chargedMicros)
    }

    @Test
    fun `parses a granted purchase verification`() {
        val result = CreditsApi.parseVerifyResponse(
            """{"granted":true,"grantedMicros":1000000,"balanceMicros":1100000}"""
        )
        assertTrue(result.granted)
        assertFalse(result.alreadyGranted)
        assertEquals(1000000L, result.grantedMicros)
        assertEquals(1100000L, result.balanceMicros)
    }

    @Test
    fun `parses an already-granted replay`() {
        val result = CreditsApi.parseVerifyResponse(
            """{"granted":false,"alreadyGranted":true,"balanceMicros":1100000}"""
        )
        assertFalse(result.granted)
        assertTrue(result.alreadyGranted)
        assertEquals(0L, result.grantedMicros)
        assertEquals(1100000L, result.balanceMicros)
    }

    @Test
    fun `missing session token in a sign-in response is an error`() {
        try {
            CreditsApi.parseAuthResponse("""{"balanceMicros":1,"isNewUser":false}""")
            fail("expected CreditsException")
        } catch (e: CreditsException.Api) {
            // expected
        }
    }

    @Test
    fun `malformed json is an error, not a crash`() {
        try {
            CreditsApi.parseBalanceResponse("not json")
            fail("expected CreditsException")
        } catch (e: CreditsException.Api) {
            // expected
        }
    }

    @Test
    fun `extracts the backend error code`() {
        assertEquals("insufficient_credits", CreditsApi.parseErrorCode("""{"error":"insufficient_credits"}"""))
        assertEquals(null, CreditsApi.parseErrorCode("""{}"""))
        assertEquals(null, CreditsApi.parseErrorCode("garbage"))
        assertEquals(null, CreditsApi.parseErrorCode(null))
    }

    @Test
    fun `status codes map to the right exception types`() {
        assertTrue(creditsErrorFor(401, "invalid_token") is CreditsException.SessionExpired)
        assertTrue(creditsErrorFor(402, "insufficient_credits") is CreditsException.InsufficientCredits)
        assertTrue(creditsErrorFor(503, "provider_not_configured") is CreditsException.NotConfigured)
        assertTrue(creditsErrorFor(500, "internal_error") is CreditsException.Network)
        assertTrue(creditsErrorFor(400, "invalid_text") is CreditsException.Api)
    }

    @Test
    fun `daily cap errors distinguish user and global caps`() {
        val userCap = creditsErrorFor(429, "user_daily_cap_exceeded") as CreditsException.DailyCap
        assertFalse(userCap.global)
        val globalCap = creditsErrorFor(429, "global_daily_cap_exceeded") as CreditsException.DailyCap
        assertTrue(globalCap.global)
    }
}
