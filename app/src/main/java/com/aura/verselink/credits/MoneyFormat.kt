package com.aura.verselink.credits

/**
 * Formats backend money amounts. The backend counts integer microdollars
 * (1 USD = 1,000,000 micros) so no float ever touches money; this formatter
 * stays in integers too and rounds the sub-cent remainder half-up purely
 * for display.
 */
object MoneyFormat {

    fun formatMicros(micros: Long): String {
        val sign = if (micros < 0) "-" else ""
        val abs = kotlin.math.abs(micros)
        // Total cents, rounding the leftover fraction of a cent half-up.
        val totalCents = (abs + 5_000) / 10_000
        val dollars = totalCents / 100
        val cents = totalCents % 100
        return "$sign\$${dollars}.${cents.toString().padStart(2, '0')}"
    }
}
