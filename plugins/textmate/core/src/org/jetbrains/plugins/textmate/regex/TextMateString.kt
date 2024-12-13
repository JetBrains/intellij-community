package org.jetbrains.plugins.textmate.regex

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import kotlin.text.toByteArray

class TextMateString private constructor(@JvmField val bytes: ByteArray) {
  @JvmField
  val id: Any = Any()

  companion object {
    @JvmStatic
    fun fromString(string: String): TextMateString {
      return TextMateString(string.toByteArray(StandardCharsets.UTF_8))
    }

    @JvmStatic
    fun fromCharSequence(string: CharSequence): TextMateString {
      val byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(string))
      val bytes = ByteArray(byteBuffer.remaining())
      byteBuffer.get(bytes)
      return TextMateString(bytes)
    }
  }

  override fun hashCode(): Int {
    return bytes.contentHashCode()
  }

  override fun equals(obj: Any?): Boolean {
    return obj is TextMateString && bytes.contentEquals(obj.bytes)
  }
}
