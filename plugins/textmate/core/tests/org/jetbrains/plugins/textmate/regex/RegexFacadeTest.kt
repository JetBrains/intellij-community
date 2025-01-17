package org.jetbrains.plugins.textmate.regex

import org.jetbrains.plugins.textmate.regex.RegexUtil.byteOffsetByCharOffset
import org.junit.Test
import kotlin.test.assertEquals

abstract class RegexFacadeTest {
  @Test
  fun matching() {
    val regex = regex("[0-9]+")
    val string = TextMateString.fromString("12:00pm")
    val match = regex.match(string, null)
    assertEquals(TextMateRange(0, 2), match.codePointRange(string.bytes))
  }

  @Test
  fun matchingFromPosition() {
    val regex = regex("[0-9]+")
    val string = TextMateString.fromString("12:00pm")
    val match = regex.match(string, 2, true, true, null)
    assertEquals(TextMateRange(3, 5), match.codePointRange(string.bytes))
  }

  @Test
  fun matchingWithGroups() {
    val regex = regex("([0-9]+):([0-9]+)")
    val string = TextMateString.fromString("12:00pm")
    val match = regex.match(string, null)
    assertEquals(TextMateRange(0, 5), match.codePointRange(string.bytes))
    assertEquals(TextMateRange(0, 2), match.codePointRange(string.bytes, 1))
    assertEquals(TextMateRange(3, 5), match.codePointRange(string.bytes, 2))
  }

  @Test
  fun cyrillicMatchingSinceIndex() {
    val regex = regex("мир")
    val text = "привет, мир; привет, мир!"
    val string = TextMateString.fromString(text)
    val match = regex.match(string, byteOffsetByCharOffset(text, 0, 9), true, true, null)
    assertEquals(TextMateRange(21, 24), match.codePointRange(string.bytes))
  }

  @Test
  fun cyrillicMatching() {
    val regex = regex("мир")
    val string = TextMateString.fromString("привет, мир!")
    val match = regex.match(string, null)
    assertEquals(TextMateRange(8, 11), match.codePointRange(string.bytes))
  }

  @Test
  fun unicodeMatching() {
    val regex = regex("мир")
    val string = "\uD83D\uDEA7\uD83D\uDEA7\uD83D\uDEA7 привет, мир!"
    val textMateString = TextMateString.fromString(string)
    val match = regex.match(textMateString, null)
    val range = match.charRange(string, textMateString.bytes)
    assertEquals("мир", string.substring(range.start, range.end))
  }

  protected abstract fun regex(s: String): RegexFacade
}
