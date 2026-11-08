package com.aura.verselink.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceExtractorTest {

    // Membership is all the extractor consults, so a subset of the real
    // book-code values (BibleService's bibleBooks map) is enough. Note
    // 1TIM is deliberately absent: 1TI is the valid code.
    private val validBookCodes = setOf("GEN", "1KI", "PSA", "MAT", "JHN", "1TI", "REV")

    @Test
    fun `chapter only reference is kept`() {
        assertEquals(listOf("1KI.18"), ReferenceExtractor.extract("1KI.18", validBookCodes))
    }

    @Test
    fun `chapter only psalm is kept`() {
        assertEquals(listOf("PSA.23"), ReferenceExtractor.extract("PSA.23", validBookCodes))
    }

    @Test
    fun `chapter range is kept`() {
        assertEquals(listOf("1KI.18-19"), ReferenceExtractor.extract("1KI.18-19", validBookCodes))
    }

    @Test
    fun `chapter and verse still works`() {
        assertEquals(listOf("JHN.3.16"), ReferenceExtractor.extract("JHN.3.16", validBookCodes))
    }

    @Test
    fun `verse range still works`() {
        assertEquals(listOf("JHN.3.16-18"), ReferenceExtractor.extract("JHN.3.16-18", validBookCodes))
    }

    @Test
    fun `mixed list keeps both in order`() {
        assertEquals(
            listOf("1KI.18", "JHN.3.16"),
            ReferenceExtractor.extract("1KI.18, JHN.3.16", validBookCodes)
        )
        assertEquals(
            listOf("JHN.3.16", "1KI.18"),
            ReferenceExtractor.extract("JHN.3.16, 1KI.18", validBookCodes)
        )
    }

    @Test
    fun `NONE yields nothing`() {
        assertEquals(emptyList<String>(), ReferenceExtractor.extract("NONE", validBookCodes))
    }

    @Test
    fun `lowercase none yields nothing`() {
        assertEquals(emptyList<String>(), ReferenceExtractor.extract("none", validBookCodes))
    }

    @Test
    fun `four letter book code is normalized to the USFM code`() {
        assertEquals(listOf("1TI.2.12"), ReferenceExtractor.extract("1TIM.2.12", validBookCodes))
    }

    @Test
    fun `prose with unrelated numbers yields nothing`() {
        assertEquals(
            emptyList<String>(),
            ReferenceExtractor.extract(
                "About 1200 people attended, and lunch cost 3.5 dollars per person in 2024.",
                validBookCodes
            )
        )
    }
}
