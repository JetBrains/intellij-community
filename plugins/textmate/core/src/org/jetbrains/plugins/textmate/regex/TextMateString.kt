package org.jetbrains.plugins.textmate.regex

import kotlin.concurrent.Volatile


interface TextMateString: AutoCloseable {
  val id: Any
  val bytesLength: Int
  fun subSequenceByByteRange(byteRange: TextMateByteRange): CharSequence
  fun charRangeByByteRange(byteRange: TextMateByteRange): TextMateCharRange
}

class TextMateStringImpl private constructor(val bytes: ByteArray): TextMateString {
  override val id: Any = Any()

  /** Lazily computed content hash; 0 stands for "not computed yet", as in `String.hashCode`. */
  private var hash: Int = 0

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

  /** 1 = every byte is ASCII, 0 = some byte is not, -1 = not checked yet. */
  private var allAscii: Byte = -1

  /**
   * The start of the last query and the char offset of that start, packed into one field:
   * the byte offset in the high 32 bits, the char offset in the low 32 bits.
   * One field keeps the two offsets consistent. A concurrent query makes the anchor stale,
   * which costs a count from zero, but it cannot mix the halves of two queries.
   */
  @Volatile
  private var anchor: Long = 0

  override fun charRangeByByteRange(byteRange: TextMateByteRange): TextMateCharRange {
    if (isAllAscii()) {
      return TextMateCharRange(byteRange.start.offset.charOffset(), byteRange.end.offset.charOffset())
    }
    val startByteOffset = byteRange.start.offset
    val packedAnchor = anchor
    val anchorByteOffset = (packedAnchor ushr 32).toInt()
    val startCharOffset = if (startByteOffset >= anchorByteOffset) {
      packedAnchor.toInt() + countUtf16Units(anchorByteOffset, startByteOffset)
    }
    else {
      countUtf16Units(0, startByteOffset)
    }
    // a group that did not take part in the match has the start -1, see MatchData.byteRange.
    // Such a start must not become the anchor: the next query would count from a negative index.
    if (startByteOffset >= 0) {
      anchor = (startByteOffset.toLong() shl 32) or startCharOffset.toLong()
    }
    val endCharOffset = startCharOffset + countUtf16Units(startByteOffset, byteRange.end.offset)
    return TextMateCharRange(startCharOffset.charOffset(), endCharOffset.charOffset())
  }

  private fun isAllAscii(): Boolean {
    var a = allAscii
    if (a < 0) {
      a = if (bytes.all { it >= 0 }) 1 else 0
      allAscii = a
    }
    return a == 1.toByte()
  }

  /**
   * Counts the UTF-16 units that the bytes in the range hold, without building a string for them.
   * A byte that is not a UTF-8 continuation byte starts one character, and a four-byte sequence
   * holds a surrogate pair, so it counts as two units.
   */
  private fun countUtf16Units(startByteOffset: Int, endByteOffset: Int): Int {
    var units = 0
    var i = startByteOffset
    while (i < endByteOffset) {
      val b = bytes[i].toInt() and 0xFF
      if (b and 0xC0 != 0x80) {
        units += if (b and 0xF8 == 0xF0) 2 else 1
      }
      i++
    }
    return units
  }

  override fun hashCode(): Int {
    var h = hash
    if (h == 0) {
      h = bytes.contentHashCode()
      if (h == 0) {
        h = 31
      }
      hash = h
    }
    return h
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TextMateStringImpl) return false
    return hashCode() == other.hashCode() && bytes.contentEquals(other.bytes)
  }

  override fun close() {
  }
}
