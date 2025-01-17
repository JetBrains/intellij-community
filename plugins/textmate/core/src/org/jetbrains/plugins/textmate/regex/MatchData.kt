package org.jetbrains.plugins.textmate.regex

import org.jetbrains.plugins.textmate.regex.RegexUtil.codePointsRangeByByteRange

data class MatchData(val matched: Boolean, private val offsets: IntArray) {
  fun count(): Int {
    return offsets.size / 2
  }

  fun byteOffset(group: Int = 0): TextMateRange {
    val endIndex = group * 2 + 1
    return TextMateRange(offsets[endIndex - 1], offsets[endIndex])
  }

  fun charRange(s: CharSequence, stringBytes: ByteArray?, group: Int = 0): TextMateRange {
    val range = codePointRange(stringBytes, group)
    return TextMateRange(Character.offsetByCodePoints(s, 0, range.start),
                         Character.offsetByCodePoints(s, 0, range.end))
  }

  fun codePointRange(stringBytes: ByteArray?, group: Int = 0): TextMateRange {
    return codePointsRangeByByteRange(stringBytes, byteOffset(group))
  }

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false

    val matchData = o as MatchData

    if (matched != matchData.matched) return false
    if (!offsets.contentEquals(matchData.offsets)) return false

    return true
  }

  override fun hashCode(): Int {
    return 31 * (if (matched) 1 else 0) + offsets.contentHashCode()
  }

  override fun toString(): String {
    return "{ matched=" + matched + ", offsets=" + offsets.contentToString() + '}'
  }

  companion object {
    val NOT_MATCHED: MatchData = MatchData(false, IntArray(0))
  }
}
