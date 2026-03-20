package com.intellij.searchEverywhereMl.typos

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SearchEverywhereSpellerImplTest {
  @Test
  fun `selectCorrections drops original query duplicates and low confidence`() {
    val corrections = listOf(
      SearchEverywhereSpellCheckResult.Correction("show color picker", 0.92),
      SearchEverywhereSpellCheckResult.Correction("show color picker", 0.88),
      SearchEverywhereSpellCheckResult.Correction("show colour picker", 0.79),
      SearchEverywhereSpellCheckResult.Correction("show colr piker", 0.95),
      SearchEverywhereSpellCheckResult.Correction("show column picker", 0.41),
    )

    val actual = selectCorrections(
      query = "show colr piker",
      corrections = corrections,
      maxCorrections = 5,
      minConfidence = 0.5,
    )

    assertEquals(
      listOf(
        SearchEverywhereSpellCheckResult.Correction("show color picker", 0.92),
        SearchEverywhereSpellCheckResult.Correction("show colour picker", 0.79),
      ),
      actual,
    )
  }

  @Test
  fun `selectCorrections applies max corrections after filtering`() {
    val corrections = listOf(
      SearchEverywhereSpellCheckResult.Correction("first", 0.95),
      SearchEverywhereSpellCheckResult.Correction("second", 0.91),
      SearchEverywhereSpellCheckResult.Correction("third", 0.89),
    )

    val actual = selectCorrections(
      query = "query",
      corrections = corrections,
      maxCorrections = 2,
      minConfidence = 0.5,
    )

    assertEquals(corrections.take(2), actual)
  }
}
