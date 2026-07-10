// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import java.text.BreakIterator
import java.util.Locale

internal object TableCharacterWidthUtils {

  fun calculateDisplayWidth(text: String): Int {
    if (text.isEmpty()) return 0
    // Iterate over grapheme clusters so that emoji sequences (ZWJ joins like 👨‍👩‍👧,
    // skin-tone modifiers like 👍🏿, regional indicator flag pairs like 🇺🇸) collapse
    // to a single visual cell-pair rather than being counted as multiple double-width units.
    val it = BreakIterator.getCharacterInstance(Locale.ROOT)
    it.setText(text)
    var width = 0
    var start = it.first()
    var end = it.next()
    while (end != BreakIterator.DONE) {
      // A cluster's visual width comes from its base codepoint; the trailing combining marks,
      // ZWJ/ZWNJ, variation selectors, modifiers, and the second RI of a flag — all add 0.
      width += getCharacterWidth(text.codePointAt(start))
      start = end
      end = it.next()
    }
    return width
  }

  private fun getCharacterWidth(codePoint: Int): Int {
    return when {
      codePoint in 0x20..0x7E -> 1
      codePoint < 0x20 -> 0
      isZeroWidth(codePoint) -> 0
      isFullWidthCharacter(codePoint) -> 2
      else -> 1
    }
  }

  // Combining marks, variation selectors, ZWJ/ZWNJ etc. don't occupy a display cell.
  private fun isZeroWidth(codePoint: Int): Boolean {
    val type = Character.getType(codePoint)
    return type == Character.NON_SPACING_MARK.toInt() ||
           type == Character.ENCLOSING_MARK.toInt() ||
           type == Character.FORMAT.toInt()
  }

  // Based on https://unicode.org/Public/UNIDATA/EastAsianWidth.txt plus common emoji blocks.
  internal fun isFullWidthCharacter(codePoint: Int): Boolean {
    return codePoint >= 0x1100 && (
      codePoint <= 0x115F ||                                      // Hangul Jamo
      codePoint == 0x2329 || codePoint == 0x232A ||              // Angle Brackets
      (codePoint in 0x231A..0x231B) ||                           // ⌚ Watch, ⌛ Hourglass
      (codePoint in 0x23E9..0x23EC) ||                           // ⏩ ⏪ ⏫ ⏬ media controls
      codePoint == 0x23F0 || codePoint == 0x23F3 ||              // ⏰ Alarm Clock, ⏳ Hourglass Not Done
      (codePoint in 0x25FD..0x25FE) ||                           // ◽ ◾ Medium White/Black Square
      (codePoint in 0x2600..0x27BF) ||                           // Misc Symbols + Dingbats (emoji-like)
      (codePoint in 0x2B05..0x2B07) ||                           // ⬅ ⬆ ⬇ Heavy Arrows
      (codePoint in 0x2B1B..0x2B1C) ||                           // Black/White Large Square ⬛ ⬜
      codePoint == 0x2B50 || codePoint == 0x2B55 ||              // White Medium Star ⭐, Heavy Large Circle ⭕
      (codePoint in 0x2E80..0x3247 && codePoint != 0x303F) ||   // CJK Radicals Supplement .. Enclosed CJK
      (codePoint in 0x3250..0x4DBF) ||                           // Enclosed CJK .. CJK Extension A
      (codePoint in 0x4E00..0xA4C6) ||                           // CJK Unified Ideographs .. Yi Radicals
      (codePoint in 0xA960..0xA97C) ||                           // Hangul Jamo Extended-A
      (codePoint in 0xAC00..0xD7A3) ||                           // Hangul Syllables
      (codePoint in 0xF900..0xFAFF) ||                           // CJK Compatibility Ideographs
      (codePoint in 0xFE10..0xFE19) ||                           // Vertical Forms
      (codePoint in 0xFE30..0xFE6B) ||                           // CJK Compatibility Forms .. Small Forms
      (codePoint in 0xFF01..0xFF60) ||                           // Halfwidth and Fullwidth Forms
      (codePoint in 0xFFE0..0xFFE6) ||                           // Fullwidth Signs
      (codePoint in 0x1B000..0x1B122) ||                         // Kana Supplement + Kana Extended-A
      codePoint == 0x1F0CF ||                                    // 🃏 Playing Card Black Joker
      codePoint == 0x1F18E ||                                    // 🆎 Negative Squared AB
      (codePoint in 0x1F191..0x1F19A) ||                         // 🆑..🆚 Squared CL, COOL, FREE, ID, NEW, NG, OK, SOS, UP!, VS
      (codePoint in 0x1F1E6..0x1F1FF) ||                         // Regional Indicators (flag halves)
      (codePoint in 0x1F200..0x1F251) ||                         // Enclosed Ideographic Supplement
      (codePoint in 0x1F300..0x1F5FF) ||                         // Misc Symbols & Pictographs
      (codePoint in 0x1F600..0x1F64F) ||                         // Emoticons
      (codePoint in 0x1F680..0x1F6FF) ||                         // Transport & Map
      (codePoint in 0x1F7E0..0x1F7EB) ||                         // Geometric Shapes Extended (large colored circles & squares)
      codePoint == 0x1F7F0 ||                                    // 🟰 Heavy Equals Sign
      (codePoint in 0x1F900..0x1F9FF) ||                         // Supplemental Symbols & Pictographs
      (codePoint in 0x1FA70..0x1FAFF) ||                         // Symbols and Pictographs Extended-A
      (codePoint in 0x20000..0x3FFFD)                            // CJK Extension B .. Tertiary Ideographic Plane
    )
  }
}
