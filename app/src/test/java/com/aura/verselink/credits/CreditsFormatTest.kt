package com.aura.verselink.credits

import org.junit.Assert.assertEquals
import org.junit.Test

class CreditsFormatTest {

    @Test
    fun `micros convert to whole credits`() {
        assertEquals(0L, CreditsFormat.creditsFromMicros(0))
        assertEquals(1L, CreditsFormat.creditsFromMicros(10_000))
        assertEquals(10L, CreditsFormat.creditsFromMicros(100_000))
        assertEquals(100L, CreditsFormat.creditsFromMicros(1_000_000))
        assertEquals(1_000L, CreditsFormat.creditsFromMicros(10_000_000))
        assertEquals(15_000L, CreditsFormat.creditsFromMicros(150_000_000))
    }

    @Test
    fun `starter grant and pack amounts`() {
        assertEquals("0 credits", CreditsFormat.formatCredits(0))
        assertEquals("1000 credits", CreditsFormat.formatCredits(10_000_000))
        assertEquals("15000 credits", CreditsFormat.formatCredits(150_000_000))
    }

    @Test
    fun `singular for exactly one credit`() {
        assertEquals("1 credit", CreditsFormat.formatCredits(10_000))
    }

    @Test
    fun `sub-credit remainders truncate`() {
        // 87 micros is a real legacy per-call charge size; it is not a
        // whole credit and displays as none of one.
        assertEquals("0 credits", CreditsFormat.formatCredits(87))
        assertEquals("1 credit", CreditsFormat.formatCredits(19_999))
    }

    @Test
    fun `negative amounts keep their sign`() {
        assertEquals("-50 credits", CreditsFormat.formatCredits(-500_000))
    }
}
