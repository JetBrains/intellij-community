package org.jetbrains.plugins.textmate.regex

import org.jetbrains.plugins.textmate.regex.RegexUtil.codePointsRangeByByteRange
import org.joni.Region
import kotlin.math.max

class MatchData private constructor(@JvmField val matched: Boolean,
                                    private val offsets: IntArray) {
  fun count(): Int {
    return offsets.size / 2
  }

  @JvmOverloads
  fun byteOffset(group: Int = 0): TextMateRange {
    val endIndex = group * 2 + 1
    return TextMateRange(offsets[endIndex - 1], offsets[endIndex])
  }

  @JvmOverloads
  fun charRange(s: CharSequence, stringBytes: ByteArray?, group: Int = 0): TextMateRange {
    val range = codePointRange(stringBytes, group)
    return TextMateRange(Character.offsetByCodePoints(s, 0, range.start),
                         Character.offsetByCodePoints(s, 0, range.end))
  }

  @JvmOverloads
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
    @JvmField
    val NOT_MATCHED: MatchData = MatchData(false, IntArray(0))

    @JvmStatic
    fun fromRegion(matchedRegion: Region?): MatchData {
      if (matchedRegion != null) {
        val offsets = IntArray(matchedRegion.numRegs * 2)
        for (i in 0..<matchedRegion.numRegs) {
          val startIndex = i * 2
          offsets[startIndex] = max(matchedRegion.getBeg(i), 0)
          offsets[startIndex + 1] = max(matchedRegion.getEnd(i), 0)
        }
        return MatchData(true, offsets)
      }
      return NOT_MATCHED
    }
  }
}
