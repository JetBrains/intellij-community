package org.jetbrains.plugins.textmate.regex

import org.jcodings.specific.NonStrictUTF8Encoding
import org.jcodings.specific.UTF8Encoding

object RegexUtil {
  @JvmStatic
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
      result += UTF8Encoding.INSTANCE.codeToMbcLength(charSequence.get(i).code)
      i++
    }
    return result
  }

  @JvmStatic
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
