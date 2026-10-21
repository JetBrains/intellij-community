package org.jetbrains.plugins.textmate.regex


interface TextMateString: AutoCloseable {
  val id: Any
  val bytesLength: Int
  fun subSequenceByByteRange(byteRange: TextMateByteRange): CharSequence
  fun charRangeByByteRange(byteRange: TextMateByteRange): TextMateCharRange
}

class TextMateStringImpl private constructor(val bytes: ByteArray): TextMateString {
  override val id: Any = Any()
  override val bytesLength: Int
    get() = bytes.size

  companion object {
    fun fromString(string: String): TextMateString {
      return TextMateStringImpl(string.encodeToByteArray())
    }
  }

  override fun subSequenceByByteRange(byteRange: TextMateByteRange): CharSequence {
    return bytes.decodeToString(byteRange.start.offset, byteRange.end.offset)
  }

  override fun charRangeByByteRange(byteRange: TextMateByteRange): TextMateCharRange {
    val startOffset = charOffsetByByteOffset(0.byteOffset(), byteRange.start)
    val endOffset = startOffset + charOffsetByByteOffset(byteRange.start, byteRange.end)
    return TextMateCharRange(startOffset, endOffset)
  }

  /**
   * Counts the UTF-16 units that the bytes in the range hold, without building a string for them.
   * A byte that is not a UTF-8 continuation byte starts one character, and a four-byte sequence
   * holds a surrogate pair, so it counts as two units.
   */
  private fun charOffsetByByteOffset(startByteOffset: TextMateByteOffset, targetByteOffset: TextMateByteOffset): TextMateCharOffset {
    if (targetByteOffset.offset <= 0) {
      return 0.charOffset()
    }
    var units = 0
    var i = startByteOffset.offset
    while (i < targetByteOffset.offset) {
      val b = bytes[i].toInt() and 0xFF
      if (b and 0xC0 != 0x80) {
        units += if (b and 0xF8 == 0xF0) 2 else 1
      }
      i++
    }
    return units.charOffset()
  }

  override fun hashCode(): Int {
    return bytes.contentHashCode()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TextMateStringImpl) return false
    return bytes.contentEquals(other.bytes)
  }

  override fun close() {
  }
}
