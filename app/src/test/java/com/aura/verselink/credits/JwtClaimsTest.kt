package com.aura.verselink.credits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class JwtClaimsTest {

    /** Builds an unsigned JWT-shaped token with the given payload JSON. */
    private fun token(payloadJson: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = encoder.encodeToString(payloadJson.toByteArray())
        return "$header.$payload.signature"
    }

    @Test
    fun `user id comes from the session token sub claim`() {
        // The backend signs {"sub": <numeric user id>} into its session JWTs,
        // and /auth/google does not return the id separately — decoding the
        // payload is how the app learns it (for Play's obfuscated account id).
        assertEquals(42L, JwtClaims.userIdFromSessionToken(token("""{"sub":"42","exp":2000000000}""")))
        assertEquals(7L, JwtClaims.userIdFromSessionToken(token("""{"sub":7}""")))
    }

    @Test
    fun `email comes from the google id token`() {
        assertEquals(
            "someone@example.com",
            JwtClaims.emailFromIdToken(token("""{"email":"someone@example.com","sub":"abc"}"""))
        )
    }

    @Test
    fun `garbage tokens decode to nothing`() {
        assertNull(JwtClaims.userIdFromSessionToken(null))
        assertNull(JwtClaims.userIdFromSessionToken(""))
        assertNull(JwtClaims.userIdFromSessionToken("not-a-jwt"))
        assertNull(JwtClaims.userIdFromSessionToken("a.!!!.c"))
        assertNull(JwtClaims.emailFromIdToken(token("""{"sub":"abc"}""")))
    }

    @Test
    fun `session expiry follows the exp claim`() {
        val valid = token("""{"sub":"1","exp":2000000000}""")
        val expired = token("""{"sub":"1","exp":1000000000}""")
        val now = 1_700_000_000L
        assertFalse(JwtClaims.isSessionExpired(valid, now))
        assertTrue(JwtClaims.isSessionExpired(expired, now))
        // No token, no expiry, or undecodable all count as expired.
        assertTrue(JwtClaims.isSessionExpired(null, now))
        assertTrue(JwtClaims.isSessionExpired(token("""{"sub":"1"}"""), now))
        assertTrue(JwtClaims.isSessionExpired("junk", now))
    }
}
