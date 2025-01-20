package org.jetbrains.plugins.textmate.regex

import org.jcodings.specific.NonStrictUTF8Encoding
import org.jcodings.specific.UTF8Encoding

object RegexUtil {
  fun byteOffsetByCharOffset(
    charSequence: CharSequence,
    startOffset: Int,
    targetOffset: Int,
  ): Int {
    if (targetOffset <= 0) {
      return 0
    }
    var result = 0
    charSequence.subSequence(startOffset, targetOffset).codePoints().forEach { codePoint ->
      result += UTF8Encoding.INSTANCE.codeToMbcLength(codePoint)
    }
    return result
  }

  fun codePointsRangeByByteRange(bytes: ByteArray?, byteRange: TextMateRange): TextMateRange {
    val startOffset = codePointOffsetByByteOffset(bytes, byteRange.start)
    val endOffset = codePointOffsetByByteOffset(bytes, byteRange.end)
    return TextMateRange(startOffset, endOffset)
  }

  private fun codePointOffsetByByteOffset(stringBytes: ByteArray?, byteOffset: Int): Int {
    if (byteOffset <= 0) {
      return 0
    }
    return NonStrictUTF8Encoding.INSTANCE.strLength(stringBytes, 0, byteOffset)
  }
}
