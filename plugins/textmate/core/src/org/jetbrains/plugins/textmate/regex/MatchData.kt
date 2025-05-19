package org.jetbrains.plugins.textmate.regex

data class MatchData(val matched: Boolean, private val offsets: IntArray) {
  fun count(): Int {
    return offsets.size / 2
  }

  fun byteRange(group: Int = 0): TextMateRange {
    val endIndex = group * 2 + 1
    return TextMateRange(offsets[endIndex - 1], offsets[endIndex])
  }

  fun charRange(textMateString: TextMateString, group: Int = 0): TextMateRange {
    return textMateString.charRangeByByteRange(byteRange(group))
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null) return false

    val matchData = other as MatchData

    if (matched != matchData.matched) return false
    if (!offsets.contentEquals(matchData.offsets)) return false

    return true
  }

  override fun hashCode(): Int {
    return 31 * (if (matched) 1 else 0) + offsets.contentHashCode()
  }

  override fun toString(): String {
    return "{ matched=$matched, offsets=${offsets.contentToString()} }"
  }

  companion object {
    val NOT_MATCHED: MatchData = MatchData(false, IntArray(0))
  }
}
