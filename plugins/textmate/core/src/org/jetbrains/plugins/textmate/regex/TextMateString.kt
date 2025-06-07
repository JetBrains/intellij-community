package org.jetbrains.plugins.textmate.regex

class TextMateString private constructor(val bytes: ByteArray) {
  val id: Any = Any()

  companion object {
    fun fromString(string: String): TextMateString {
      return TextMateString(string.encodeToByteArray())
    }
  }

  fun charRangeByByteRange(byteRange: TextMateRange): TextMateRange {
    val startOffset = charOffsetByByteOffset(bytes, 0, byteRange.start)
    val endOffset = startOffset + charOffsetByByteOffset(bytes, byteRange.start, byteRange.end)
    return TextMateRange(startOffset, endOffset)
  }

  fun charOffsetByByteOffset(stringBytes: ByteArray, startByteOffset: Int, targetByteOffset: Int): Int {
    return if (targetByteOffset <= 0) {
      0
    }
    else {
      stringBytes.decodeToString(startByteOffset, targetByteOffset).length
    }
  }

  override fun hashCode(): Int {
    return bytes.contentHashCode()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TextMateString) return false
    return bytes.contentEquals(other.bytes)
  }
}
