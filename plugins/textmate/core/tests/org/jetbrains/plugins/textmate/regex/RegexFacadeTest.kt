package org.jetbrains.plugins.textmate.regex

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

abstract class RegexFacadeTest {
  @Test
  fun matching() {
    val regex = regex("[0-9]+")
    val string = TextMateString.fromString("12:00pm")
    val match = regex.match(string, null)
    assertEquals(TextMateRange(0, 2), match.charRange(string))
  }

  @Test
  fun matchingFromPosition() {
    val regex = regex("[0-9]+")
    val string = TextMateString.fromString("12:00pm")
    val match = regex.match(string, 2, true, true, null)
    assertEquals(TextMateRange(3, 5), match.charRange(string))
  }

  @Test
  fun matchingWithGroups() {
    val regex = regex("([0-9]+):([0-9]+)")
    val string = TextMateString.fromString("12:00pm")
    val match = regex.match(string, null)
    assertEquals(TextMateRange(0, 5), match.charRange(string))
    assertEquals(TextMateRange(0, 2), match.charRange(string, 1))
    assertEquals(TextMateRange(3, 5), match.charRange(string, 2))
  }

  @Test
  fun cyrillicMatchingSinceIndex() {
    val regex = regex("мир")
    val text = "привет, мир; привет, мир!"
    val string = TextMateString.fromString(text)
    val match = regex.match(string, byteOffsetByCharOffset(text, 0, 9), true, true, null)
    assertEquals(TextMateRange(21, 24), match.charRange(string))
  }

  @Test
  fun cyrillicMatching() {
    val regex = regex("мир")
    val string = TextMateString.fromString("привет, мир!")
    val match = regex.match(string, null)
    assertEquals(TextMateRange(8, 11), match.charRange(string))
  }

  @Test
  fun unicodeMatching() {
    val regex = regex("мир")
    val string = "\uD83D\uDEA7\uD83D\uDEA7\uD83D\uDEA7 привет, мир!"
    val textMateString = TextMateString.fromString(string)
    val match = regex.match(textMateString, null)
    val range = match.charRange(textMateString)
    assertEquals("мир", string.substring(range.start, range.end))
  }

  protected abstract fun regex(s: String): RegexFacade
}
