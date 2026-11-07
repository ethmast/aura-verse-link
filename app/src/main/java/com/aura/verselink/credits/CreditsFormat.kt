package com.aura.verselink.credits

/**
 * Formats backend balances as whole credits. The backend counts integer
 * microdollars (1 USD = 1,000,000 micros), but credits are the only unit
 * the app ever shows: 1 credit = 10,000 micros. Balances move in whole
 * credits (1,000-credit starter grant, 15,000-credit pack, 1 credit per
 * verse lookup), so the conversion is exact; any sub-credit remainder is a
 * backend accounting detail and truncates away for display.
 */
object CreditsFormat {

    const val MICROS_PER_CREDIT: Long = 10_000

    /** Whole credits in a backend microdollar amount. */
    fun creditsFromMicros(micros: Long): Long = micros / MICROS_PER_CREDIT

    /** "0 credits", "1 credit", "2 credits". */
    fun formatCredits(micros: Long): String {
        val credits = creditsFromMicros(micros)
        return if (credits == 1L) "1 credit" else "$credits credits"
    }
}
