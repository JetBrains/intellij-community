// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

/**
 * Utility class for calculating character display width in Markdown tables.
 * Handles proper width calculation for CJK and other full-width characters.
 */
internal object TableCharacterWidthUtils {

  /**
   * Calculates the display width of a string, considering different character widths.
   * Full-width characters (CJK, etc.) are counted as 2 units,
   * while half-width characters (ASCII, etc.) are counted as 1 unit.
   *
   * @param text The text to measure
   * @return The display width in character units
   */
  fun calculateDisplayWidth(text: String): Int {
    if (text.isEmpty()) return 0

    var width = 0
    var i = 0
    while (i < text.length) {
      val codePoint = text.codePointAt(i)
      width += getCharacterWidth(codePoint)
      i += Character.charCount(codePoint)
    }
    return width
  }

  private fun getCharacterWidth(codePoint: Int): Int {
    return when {
      // ASCII printable characters (half-width)
      codePoint in 0x20..0x7E -> 1

      // Control characters
      codePoint < 0x20 -> 0

      // Full-width characters (CJK, emoji, etc.)
      isFullWidthCharacter(codePoint) -> 2

      // Default to 1 for other characters (Latin Extended, Cyrillic, etc.)
      else -> 1
    }
  }

  internal fun isFullWidthCharacter(codePoint: Int): Boolean {
    return when {
      // CJK Unified Ideographs
      codePoint in 0x4E00..0x9FFF -> true

      // CJK Extension A
      codePoint in 0x3400..0x4DBF -> true

      // CJK Extension B
      codePoint in 0x20000..0x2A6DF -> true

      // CJK Extension C
      codePoint in 0x2A700..0x2B73F -> true

      // CJK Extension D
      codePoint in 0x2B740..0x2B81F -> true

      // CJK Extension E
      codePoint in 0x2B820..0x2CEAF -> true

      // CJK Extension F
      codePoint in 0x2CEB0..0x2EBEF -> true

      // CJK Compatibility Ideographs
      codePoint in 0xF900..0xFAFF -> true

      // CJK Compatibility Ideographs Supplement
      codePoint in 0x2F800..0x2FA1F -> true

      // Hiragana
      codePoint in 0x3040..0x309F -> true

      // Katakana
      codePoint in 0x30A0..0x30FF -> true

      // Katakana Phonetic Extensions
      codePoint in 0x31F0..0x31FF -> true

      // Hangul Syllables
      codePoint in 0xAC00..0xD7AF -> true

      // Hangul Jamo
      codePoint in 0x1100..0x11FF -> true

      // Hangul Jamo Extended-A
      codePoint in 0xA960..0xA97F -> true

      // Hangul Jamo Extended-B
      codePoint in 0xD7B0..0xD7FF -> true

      // Fullwidth ASCII variants (FF01-FF60) and Fullwidth brackets (FF5F-FF60)
      codePoint in 0xFF01..0xFF60 -> true

      // Fullwidth signs (FFE0-FFE6)
      codePoint in 0xFFE0..0xFFE6 -> true

      // Emoji
      codePoint in 0x1F600..0x1F64F -> true  // Emoticons
      codePoint in 0x1F300..0x1F5FF -> true  // Misc Symbols and Pictographs
      codePoint in 0x1F680..0x1F6FF -> true  // Transport and Map
      codePoint in 0x1F1E0..0x1F1FF -> true  // Regional Indicator Symbols

      else -> false
    }
  }
}
