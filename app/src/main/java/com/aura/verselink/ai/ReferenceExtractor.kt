package com.aura.verselink.ai

/**
 * Parses the extractor model's raw reply into validated USFM references.
 *
 * The model is instructed to answer with comma-separated codes such as
 * "JHN.3.16", or "NONE" when it finds nothing. Its output is not trusted
 * blindly: candidates are scanned out of the (uppercased) reply, then each
 * book code is validated against [validBookCodes] (the values of the
 * BibleService book map).
 *
 * Accepted shapes:
 *  - BOOK.CHAPTER.VERSE        (JHN.3.16)
 *  - BOOK.CHAPTER.VERSE-VERSE  (JHN.3.16-18, a verse range in one chapter)
 *  - BOOK.CHAPTER              (1KI.18, only a chapter was mentioned)
 *  - BOOK.CHAPTER-CHAPTER      (1KI.18-19, a chapter range)
 *
 * Models often answer with up-to-4-char book codes (e.g. 1TIM instead of
 * 1TI), so each candidate is normalized by trying the extracted code
 * as-is, then progressively shorter prefixes, until a known USFM code
 * matches. The lookbehind in the pattern prevents matching TIM inside
 * 1TIM.2.12 as a separate candidate.
 */
object ReferenceExtractor {

    private val USFM_REGEX =
        Regex("(?<![A-Z0-9])[A-Z0-9]{2,4}\\.[0-9]+(?:\\.[0-9]+(?:-[0-9]+)?|-[0-9]+)?")

    /**
     * Returns the validated references found in [rawResponse], in the
     * order they appear, duplicates removed. "NONE" (or anything with no
     * valid candidate) yields an empty list.
     */
    fun extract(rawResponse: String, validBookCodes: Set<String>): List<String> {
        return USFM_REGEX.findAll(rawResponse.uppercase())
            .mapNotNull { match ->
                val bookCode = match.value.substringBefore(".")
                val rest = match.value.substringAfter(".")
                val validCode = (bookCode.length downTo 2)
                    .map { bookCode.take(it) }
                    .firstOrNull { it in validBookCodes }
                validCode?.let { "$it.$rest" }
            }
            .distinct()
            .toList()
    }
}
