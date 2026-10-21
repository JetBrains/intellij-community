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

  private fun isBoundary(bytes: ByteArray, offset: Int): Boolean {
    return offset == bytes.size || (bytes[offset].toInt() and 0xC0) != 0x80
  }
}
