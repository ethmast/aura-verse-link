package com.aura.verselink.credits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignInFeedbackTest {

    @Test
    fun `new user with a starter grant gets the announcement with the real amount`() {
        val starterMicros = 1_000L * CreditsFormat.MICROS_PER_CREDIT
        assertEquals(
            "1000 credits were added to your account",
            SignInFeedback.starterGrantMessage(isNewUser = true, balanceMicros = starterMicros)
        )
    }

    @Test
    fun `new user with zero balance gets no announcement`() {
        // The tombstone path: the backend re-creates the account after a
        // deletion but grants nothing, so the balance is 0 and no
        // "credits added" toast may fire.
        assertNull(SignInFeedback.starterGrantMessage(isNewUser = true, balanceMicros = 0L))
    }

    @Test
    fun `returning user gets no announcement even with a balance`() {
        val packMicros = 15_000L * CreditsFormat.MICROS_PER_CREDIT
        assertNull(SignInFeedback.starterGrantMessage(isNewUser = false, balanceMicros = packMicros))
    }
}
