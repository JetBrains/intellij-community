package com.intellij.searchEverywhereMl.typos.models

import com.intellij.searchEverywhereMl.typos.shouldSkipTypoCorrection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ActionsTypoSharedIndexTest {
  @Test
  fun `shared index keeps dictionary frequencies and preserves short tokens for sentence views`() {
    val sharedIndex = ActionsTypoSharedIndex.create(
      listOf(
        listOf("show", "color", "picker"),
        listOf("show", "color"),
        listOf("go", "to", "action"),
      )
    )

    val dictionary = sharedIndex.asDictionary()

    assertEquals(2, dictionary.getFrequency("show"))
    assertEquals(2, dictionary.getFrequency("color"))
    assertEquals(1, dictionary.getFrequency("picker"))
    assertNull(dictionary.getFrequency("go"))
    assertEquals(6, dictionary.totalFrequency)
    assertEquals(2, dictionary.maxFrequency)
    assertEquals(
      setOf("show", "color", "picker", "action"),
      dictionary.allWords,
    )
    assertEquals(
      listOf("show color picker", "show color", "go to action"),
      sharedIndex.sentenceTexts().toList(),
    )
  }

  @Test
  fun `prefix matcher matches exact and partial phrase prefixes`() {
    val prefixMatcher = ActionsTypoSharedIndex.create(
      listOf(
        listOf("show", "color", "picker"),
        listOf("runtime"),
        listOf("go", "to", "action"),
      )
    ).createPrefixMatcher()

    assertTrue(prefixMatcher.hasPrefix("runti"))
    assertTrue(prefixMatcher.hasPrefix("runtime"))
    assertTrue(prefixMatcher.hasPrefix("show col"))
    assertTrue(prefixMatcher.hasPrefix("color"))
    assertTrue(prefixMatcher.hasPrefix("go to"))
  }

  @Test
  fun `prefix matcher rejects typos and mid word substrings`() {
    val prefixMatcher = ActionsTypoSharedIndex.create(
      listOf(
        listOf("show", "color", "picker"),
        listOf("runtime"),
      )
    ).createPrefixMatcher()

    assertFalse(prefixMatcher.hasPrefix("rantime"))
    assertFalse(prefixMatcher.hasPrefix("olor"))
    assertFalse(prefixMatcher.hasPrefix("show colr"))
  }

  @Test
  fun `shouldSkipTypoCorrection normalizes separators and case`() {
    val prefixMatcher = ActionsTypoSharedIndex.create(
      listOf(
        listOf("show", "color", "picker"),
        listOf("runtime"),
      )
    ).createPrefixMatcher()

    assertTrue(shouldSkipTypoCorrection("Show-Col", prefixMatcher))
    assertTrue(shouldSkipTypoCorrection("color", prefixMatcher))
    assertTrue(shouldSkipTypoCorrection("RUNTI", prefixMatcher))
    assertFalse(shouldSkipTypoCorrection("123", prefixMatcher))
    assertFalse(shouldSkipTypoCorrection("Show Colr", prefixMatcher))
  }
}
