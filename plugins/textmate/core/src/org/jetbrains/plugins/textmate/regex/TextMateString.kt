package org.jetbrains.plugins.textmate.regex


interface TextMateString: AutoCloseable {
  val id: Any
  val bytes: ByteArray
  fun charRangeByByteRange(byteRange: TextMateByteRange): TextMateCharRange
  fun charOffsetByByteOffset(startByteOffset: TextMateByteOffset, targetByteOffset: TextMateByteOffset): TextMateCharOffset
}

class TextMateStringImpl private constructor(override val bytes: ByteArray): TextMateString {
  override val id: Any = Any()

  companion object {
    fun fromString(string: String): TextMateString {
      return TextMateStringImpl(string.encodeToByteArray())
    }
  }

  override fun charRangeByByteRange(byteRange: TextMateByteRange): TextMateCharRange {
    val startOffset = charOffsetByByteOffset(0.byteOffset(), byteRange.start)
    val endOffset = startOffset + charOffsetByByteOffset(byteRange.start, byteRange.end)
    return TextMateCharRange(startOffset, endOffset)
  }

  override fun charOffsetByByteOffset(startByteOffset: TextMateByteOffset, targetByteOffset: TextMateByteOffset): TextMateCharOffset {
    return if (targetByteOffset.offset <= 0) {
      0.charOffset()
    }
    else {
      bytes.decodeToString(startByteOffset.offset, targetByteOffset.offset).length.charOffset()
    }
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
