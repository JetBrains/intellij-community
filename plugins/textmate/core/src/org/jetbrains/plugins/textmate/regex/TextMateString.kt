package org.jetbrains.plugins.textmate.regex

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import kotlin.text.toByteArray

class TextMateString private constructor(val bytes: ByteArray) {
  val id: Any = Any()

  companion object {
    fun fromString(string: String): TextMateString {
      return TextMateString(string.toByteArray(StandardCharsets.UTF_8))
    }

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
