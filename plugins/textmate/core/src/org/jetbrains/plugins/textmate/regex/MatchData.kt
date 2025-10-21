package org.jetbrains.plugins.textmate.regex

data class MatchData(val matched: Boolean,
                     private val byteOffsets: IntArray) {
  fun count(): Int {
    return byteOffsets.size / 2
  }

  fun byteRange(group: Int = 0): TextMateByteRange {
    val endIndex = group * 2 + 1
    return TextMateByteRange(byteOffsets[endIndex - 1].byteOffset(), byteOffsets[endIndex].byteOffset())
  }

  fun charRange(textMateString: TextMateString, group: Int = 0): TextMateCharRange {
    return textMateString.charRangeByByteRange(byteRange(group))
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null) return false

    val matchData = other as MatchData

    if (matched != matchData.matched) return false
    if (!byteOffsets.contentEquals(matchData.byteOffsets)) return false

    return true
  }

  override fun hashCode(): Int {
    return 31 * (if (matched) 1 else 0) + byteOffsets.contentHashCode()
  }

  override fun toString(): String {
    return "{ matched=$matched, offsets=${byteOffsets.contentToString()} }"
  }

  companion object {
    val NOT_MATCHED: MatchData = MatchData(false, IntArray(0))
  }
}
