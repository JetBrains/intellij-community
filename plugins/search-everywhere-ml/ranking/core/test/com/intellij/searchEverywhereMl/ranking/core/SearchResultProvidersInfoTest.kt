package com.intellij.searchEverywhereMl.ranking.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.intellij.testFramework.junit5.TestApplication

@TestApplication
internal class SearchResultProvidersInfoTest {
  @Test
  fun `split session providers info is always mixed with priorities`() {
    val info = SearchResultProvidersInfo.forSplitSession()

    assertTrue(info.isMixedList)
    assertFalse(info.providerPriorities.isEmpty())
  }
}
