package com.intellij.searchEverywhereMl.typos

import com.intellij.searchEverywhereMl.typos.SearchEverywhereStringToken.Delimiter
import com.intellij.searchEverywhereMl.typos.SearchEverywhereStringToken.Word
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SearchEverywhereStringTokenizerTest {
  @Test
  fun `empty string yields no tokens`() {
    assertTrue(splitText("").toList().isEmpty())
  }

  @Test
  fun `blank string yields delimiter tokens`() {
    val expected = listOf(Delimiter(" "), Delimiter("\t"), Delimiter("\n"))
    val actual = splitText(" \t\n").toList()

    assertEquals(expected, actual)
  }

  @Test
  fun `words are split on case change`() {
    val expected = listOf(Word("Hello"), Word("Brave"), Word("New"), Word("World"))
    val actual = splitText("HelloBraveNewWorld").toList()

    assertEquals(expected, actual)
  }

  @Test
  fun `words are split by delimiters`() {
    val expected = listOf(Word("hello"), Delimiter("."),
                          Word("brave"), Delimiter("_"),
                          Word("new"), Delimiter("-"),
                          Word("world"))
    val actual = splitText("hello.brave_new-world").toList()

    assertEquals(expected, actual)
  }

  @Test
  fun `multiple consecutive delimiters are accepted and yielded`() {
    val expected = listOf(Word("hello"), Delimiter("."), Delimiter("_"),
                          Word("world"))
    val actual = splitText("hello._world").toList()

    assertEquals(expected, actual)
  }

  @Test
  fun `multiple consecutive spaces are accepted and yielded back in the original form`() {
    val expected = listOf(Word("hello"), Delimiter(" "), Delimiter(" "),
                          Word("world"))
    val actual = splitText("hello  world").toList()
    assertEquals(expected, actual)
  }

  @Test
  fun `all caps are yielded as a single word`() {
    val expected = listOf(Word("HBNW"))
    val actual = splitText("HBNW").toList()

    assertEquals(expected, actual)
  }

  @Test
  fun `delimiter at the end gets yielded`() {
    val expected = listOf(Word("hello"), Delimiter("."),
                          Word("world"), Delimiter("."))
    val actual = splitText("hello.world.").toList()

    assertEquals(expected, actual)
  }

  @Test
  fun `space and capital letter`() {
    val expected = listOf(Word("hello"), Delimiter(" "), Word("World"))
    val actual = splitText("hello World").toList()

    assertEquals(expected, actual)
  }
}