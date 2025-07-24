package org.jetbrains.plugins.textmate.regex

import kotlin.jvm.JvmInline

@JvmInline
value class TextMateCharOffset(val offset: Int): Comparable<TextMateCharOffset> {
  operator fun plus(other: TextMateCharOffset): TextMateCharOffset {
    return TextMateCharOffset(offset + other.offset)
  }

  operator fun minus(other: TextMateCharOffset): TextMateCharOffset {
    return TextMateCharOffset(offset - other.offset)
  }

  override fun compareTo(other: TextMateCharOffset): Int {
    return offset.compareTo(other.offset)
  }
}

@JvmInline
value class TextMateByteOffset(val offset: Int): Comparable<TextMateByteOffset> {
  operator fun plus(other: TextMateByteOffset): TextMateByteOffset {
    return TextMateByteOffset(offset + other.offset)
  }

  override fun compareTo(other: TextMateByteOffset): Int {
    return offset.compareTo(other.offset)
  }
}

internal fun Int.byteOffset(): TextMateByteOffset = TextMateByteOffset(this)
internal fun Int.charOffset(): TextMateCharOffset = TextMateCharOffset(this)

internal operator fun CharSequence.get(offset: TextMateCharOffset): Char = get(offset.offset)
internal fun CharSequence.subSequence(start: TextMateCharOffset, end: TextMateCharOffset): CharSequence = subSequence(start.offset, end.offset)
internal fun CharSequence.subSequence(range: TextMateCharRange): CharSequence = subSequence(range.start.offset, range.end.offset)
internal fun CharSequence.indexOf(char: Char, startIndex: TextMateCharOffset): TextMateCharOffset {
  return indexOf(char, startIndex = startIndex.offset).charOffset()
}

data class TextMateCharRange(
  val start: TextMateCharOffset,
  val end: TextMateCharOffset,
) {
  val isEmpty: Boolean
    get() = start.offset == end.offset

  val length: Int
    get() = end.offset - start.offset
}

data class TextMateByteRange(
  val start: TextMateByteOffset,
  val end: TextMateByteOffset,
) {
  val isEmpty: Boolean
    get() = start.offset == end.offset

  val length: Int
    get() = end.offset - start.offset
}
