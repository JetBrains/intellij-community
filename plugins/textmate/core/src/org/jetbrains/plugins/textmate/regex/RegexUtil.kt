package org.jetbrains.plugins.textmate.regex

import kotlin.Char.Companion.MIN_HIGH_SURROGATE
import kotlin.Char.Companion.MIN_LOW_SURROGATE

internal fun byteOffsetByCharOffset(
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