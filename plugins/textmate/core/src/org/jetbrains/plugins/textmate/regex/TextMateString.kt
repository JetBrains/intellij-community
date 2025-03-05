package org.jetbrains.plugins.textmate.regex

import java.nio.ByteBuffer
import java.nio.CharBuffer

class TextMateString private constructor(val bytes: ByteArray) {
  val id: Any = Any()

  companion object {
    fun fromString(string: String): TextMateString {
      return TextMateString(string.toByteArray(Charsets.UTF_8))
    }

    fun fromCharSequence(string: CharSequence): TextMateString {
      val byteBuffer = Charsets.UTF_8.encode(CharBuffer.wrap(string))
      val bytes = ByteArray(byteBuffer.remaining())
      byteBuffer.get(bytes)
      return TextMateString(bytes)
    }
  }

  fun charRangeByByteRange(byteRange: TextMateRange): TextMateRange {
    val startOffset = charOffsetByByteOffset(bytes, 0, byteRange.start)
    val endOffset = startOffset + charOffsetByByteOffset(bytes, byteRange.start, byteRange.end)
    return TextMateRange(startOffset, endOffset)
  }

  private fun charOffsetByByteOffset(stringBytes: ByteArray, startByteOffset: Int, targetByteOffset: Int): Int {
    if (targetByteOffset <= 0) {
      return 0
    }
    return Charsets.UTF_8.decode(ByteBuffer.wrap(stringBytes, startByteOffset, targetByteOffset - startByteOffset)).remaining()
  }

  override fun hashCode(): Int {
    return bytes.contentHashCode()
  }


  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as TextMateString
    return bytes.contentEquals(other.bytes)
  }
}
