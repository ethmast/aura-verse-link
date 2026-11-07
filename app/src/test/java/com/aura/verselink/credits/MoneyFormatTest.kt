package com.aura.verselink.credits

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {

    @Test
    fun `zero and starter-grant amounts`() {
        assertEquals("$0.00", MoneyFormat.formatMicros(0))
        assertEquals("$0.10", MoneyFormat.formatMicros(100_000))
    }

    @Test
    fun `whole dollars and cents`() {
        assertEquals("$1.00", MoneyFormat.formatMicros(1_000_000))
        assertEquals("$1.23", MoneyFormat.formatMicros(1_230_000))
        assertEquals("$12.05", MoneyFormat.formatMicros(12_050_000))
    }

    @Test
    fun `sub-cent remainders round half-up for display`() {
        // 87 micros is a real per-call charge size; it displays as $0.00.
        assertEquals("$0.00", MoneyFormat.formatMicros(87))
        assertEquals("$0.01", MoneyFormat.formatMicros(9_999))
        // $1.999999 rounds to $2.00 at cent precision.
        assertEquals("$2.00", MoneyFormat.formatMicros(1_999_999))
        assertEquals("$1.99", MoneyFormat.formatMicros(1_994_999))
    }

    @Test
    fun `negative amounts keep their sign`() {
        assertEquals("-$0.50", MoneyFormat.formatMicros(-500_000))
    }
}
