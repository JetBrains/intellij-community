package org.jetbrains.plugins.textmate.regex

import kotlin.test.Test
import kotlin.test.assertEquals

class TextMateStringImplTest {
  /**
   * [TextMateStringImpl.charRangeByByteRange] counts the UTF-16 units of a byte range without
   * decoding it, so the count must agree with the length of the decoded text.
   */
  // the samples are UTF-8 encoding fixtures, not words
  @Suppress("NonAsciiCharacters")
  @Test
  fun `a char range agrees with the decoded text`() {
    // ASCII, two-byte, three-byte, and four-byte sequences
    for (text in listOf("abc", "привет", "日本語", "a🚧b", "🚧🚧", "aд日🚧z")) {
      val bytes = text.encodeToByteArray()
      val string = TextMateStringImpl.fromString(text)
      for (start in 0..bytes.size) {
        for (end in start..bytes.size) {
          // only a range on a character boundary can come out of a regex match
          if (!isBoundary(bytes, start) || !isBoundary(bytes, end)) continue
          val range = string.charRangeByByteRange(TextMateByteRange(start.byteOffset(), end.byteOffset()))
          val expectedStart = bytes.decodeToString(0, start).length
          val expectedEnd = expectedStart + bytes.decodeToString(start, end).length
          assertEquals(expectedStart, range.start.offset, "start of [$start, $end] in '$text'")
          assertEquals(expectedEnd, range.end.offset, "end of [$start, $end] in '$text'")
        }
      }
    }
  }

  /**
   * [TextMateStringImpl.charRangeByByteRange] remembers the start of the last query as an anchor
   * and counts forward from it. A query left of the anchor must stay correct.
   */
  // the samples are UTF-8 encoding fixtures, not words
  @Suppress("NonAsciiCharacters")
  @Test
  fun `a char range is correct after a query further right`() {
    for (text in listOf("привет", "aд日🚧zд日", "🚧a🚧b🚧")) {
      val bytes = text.encodeToByteArray()
      val string = TextMateStringImpl.fromString(text)
      val boundaries = (0..bytes.size).filter { isBoundary(bytes, it) }
      for (start in boundaries.asReversed()) {
        val range = string.charRangeByByteRange(TextMateByteRange(start.byteOffset(), bytes.size.byteOffset()))
        val expectedStart = bytes.decodeToString(0, start).length
        assertEquals(expectedStart, range.start.offset, "start of [$start, ${bytes.size}] in '$text'")
        assertEquals(text.length, range.end.offset, "end of [$start, ${bytes.size}] in '$text'")
      }
    }
  }

  /**
   * [TextMateStringImpl.charRangeByByteRange] remembers the start of the last query as an anchor.
   * A query left of the anchor, and then one right of it, must both stay correct.
   */
  // the samples are UTF-8 encoding fixtures, not words
  @Suppress("NonAsciiCharacters")
  @Test
  fun `a char range is correct after a backward and then a forward query`() {
    for (text in listOf("привет", "aд日🚧zд日", "🚧a🚧b🚧")) {
      val bytes = text.encodeToByteArray()
      val string = TextMateStringImpl.fromString(text)
      val boundaries = (0..bytes.size).filter { isBoundary(bytes, it) }
      for (first in boundaries) {
        for (second in boundaries) {
          for (third in boundaries) {
            for (start in listOf(first, second, third)) {
              val range = string.charRangeByByteRange(TextMateByteRange(start.byteOffset(), bytes.size.byteOffset()))
              val expectedStart = bytes.decodeToString(0, start).length
              assertEquals(expectedStart, range.start.offset, "start of $start in the sequence $first, $second, $third in '$text'")
              assertEquals(text.length, range.end.offset, "end of $start in the sequence $first, $second, $third in '$text'")
            }
          }
        }
      }
    }
  }

  /**
   * A group that did not take part in the match has the byte range (-1, -1), see [MatchData.byteRange].
   * The query for such a group must not break the queries that follow it.
   */
  @Test
  fun `a char range of a group that did not match keeps the later queries correct`() {
    val text = "привет-мир"
    val string = TextMateStringImpl.fromString(text)
    // group 1 holds the 12 bytes of the first word, group 2 did not take part in the match
    val matchData = MatchData(matched = true, byteOffsets = intArrayOf(0, 19, 0, 12, -1, -1))

    val emptyGroup = matchData.charRange(string, 2)
    assertEquals(0, emptyGroup.start.offset, "start of the group that did not match")
    assertEquals(0, emptyGroup.end.offset, "end of the group that did not match")

    val firstGroup = matchData.charRange(string, 1)
    assertEquals(0, firstGroup.start.offset, "start of group 1")
    assertEquals(6, firstGroup.end.offset, "end of group 1")
  }

  private fun isBoundary(bytes: ByteArray, offset: Int): Boolean {
    return offset == bytes.size || (bytes[offset].toInt() and 0xC0) != 0x80
  }
}
