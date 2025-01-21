package org.jetbrains.plugins.textmate.regex

import java.nio.ByteBuffer
import kotlin.Char.Companion.MIN_HIGH_SURROGATE
import kotlin.Char.Companion.MIN_LOW_SURROGATE

fun byteOffsetByCharOffset(
  charSequence: CharSequence,
  startOffset: Int,
  targetOffset: Int,
): Int {
  if (targetOffset <= 0) {
    return 0
  }
  var result = 0
  var i = startOffset
  while (i < targetOffset) {
    val char = charSequence[i]
    if (char.isHighSurrogate() && i + 1 < charSequence.length && charSequence[i + 1].isLowSurrogate()) {
      result += utf8Size(codePoint(char, charSequence[i + 1]))
      i++ // Skip the low surrogate
    }
    else {
      result += utf8Size(char.code)
    }
    i++
  }
  return result
}

fun charRangeByByteRange(bytes: ByteArray, byteRange: TextMateRange): TextMateRange {
  val startOffset = charOffsetByByteOffset(bytes, 0, byteRange.start)
  val endOffset = startOffset + charOffsetByByteOffset(bytes, byteRange.start, byteRange.end)
  return TextMateRange(startOffset, endOffset)
}

private fun charOffsetByByteOffset(stringBytes: ByteArray, startByteOffset: Int, targetByteOffset: Int): Int {
  if (targetByteOffset <= 0) {
    return 0
  }
  return Charsets.UTF_8.decode(ByteBuffer.wrap(stringBytes, startByteOffset, targetByteOffset - startByteOffset)).remaining()
}

private fun utf8Size(codePoint: Int): Int {
  return when {
    codePoint <= 0x7F -> 1 // 1 byte for ASCII
    codePoint <= 0x7FF -> 2 // 2 bytes for U+0080 to U+07FF
    codePoint <= 0xFFFF -> 3 // 3 bytes for U+0800 to U+FFFF
    else -> 4
  }
}

private fun codePoint(high: Char, low: Char): Int {
  return (((high - MIN_HIGH_SURROGATE) shl 10) or (low - MIN_LOW_SURROGATE)) + 0x10000
}