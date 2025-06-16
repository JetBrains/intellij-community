package org.jetbrains.plugins.textmate.regex


interface TextMateString: AutoCloseable {
  val id: Any
  val bytes: ByteArray
  fun charRangeByByteRange(byteRange: TextMateRange): TextMateRange
  fun charOffsetByByteOffset(startByteOffset: Int, targetByteOffset: Int): Int
}

class TextMateStringImpl private constructor(override val bytes: ByteArray): TextMateString {
  override val id: Any = Any()

  companion object {
    fun fromString(string: String): TextMateString {
      return TextMateStringImpl(string.encodeToByteArray())
    }
  }

  override fun charRangeByByteRange(byteRange: TextMateRange): TextMateRange {
    val startOffset = charOffsetByByteOffset(0, byteRange.start)
    val endOffset = startOffset + charOffsetByByteOffset(byteRange.start, byteRange.end)
    return TextMateRange(startOffset, endOffset)
  }

  override fun charOffsetByByteOffset(startByteOffset: Int, targetByteOffset: Int): Int {
    return if (targetByteOffset <= 0) {
      0
    }
    else {
      bytes.decodeToString(startByteOffset, targetByteOffset).length
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
