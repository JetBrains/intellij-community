// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * How grapheme clusters are surfaced by [TerminalRow.toStyledText]. The engine stores each cell's full
 * grapheme cluster (its base code point plus the trailing code points), and [TerminalRow.toStyledText]
 * reconstructs it, so a row's text presentation round-trips the written text: combining marks, ZWJ emoji
 * sequences, skin-tone modifiers, and regional-indicator flags all read back exactly as written.
 *
 * These behaviors reflect the bundled libghostty-vt and may need updating if the engine changes.
 */
class GraphemeClusterTest {

  private fun cp(vararg codePoints: Int): String = buildString { for (c in codePoints) appendCodePoint(c) }

  /** Writes [input] into a fresh row and returns its [TerminalRow.toStyledText] text. */
  private fun presentedText(input: String): String {
    var text = ""
    session(30, 2) { session ->
      session.write(input)
      text = session.screenLine(0).toStyledText().text
    }
    return text
  }

  /**
   * Attribute ranges follow the chars a cluster contributes, not its cell count: a one-cell cluster of a
   * base letter plus a combining accent occupies two `char`s of the text, and its style range covers both.
   */
  @Test
  fun `a styled grapheme cluster is covered over all its chars`() = session(30, 2) { session ->
    val cluster = cp(0x61, 0x0301) // 'a' + combining acute accent: one cell, two chars
    session.write(csi("31m") + cluster + csi("0m") + "x")

    val styled = session.screenLine(0).toStyledText()
    assertThat(styled.text).isEqualTo(cluster + "x")
    assertThat(styled.styleRanges).hasSize(1)
    assertThat(styled.styleRanges.single().startOffset).isEqualTo(0)
    assertThat(styled.styleRanges.single().endOffset).isEqualTo(cluster.length)
  }

  @Test
  fun `combining acute accent is shown`() {
    // 'a' + U+0301 (one grapheme cluster in one cell): the base letter and its accent are both surfaced.
    val text = cp(0x61, 0x0301)
    assertThat(presentedText(text)).isEqualTo(text)
  }

  @Test
  fun `combining acute accents across a whole row are shown`() {
    // Every character of "1:abcdefgh" carries a U+0301 combining acute accent; the row presents each base
    // character followed by its accent -- exactly the text that was written (1́:́áb́ćd́éf́ǵh́).
    val text = buildString {
      for (c in "abcdefgh") {
        append(c)
        appendCodePoint(0x0301)
      }
    }
    assertThat(presentedText(text)).isEqualTo(text)
  }

  @Test
  fun `stacked combining marks are shown`() {
    // 'a' + U+0301 + U+0323: multiple combining marks on one base are all surfaced, in order.
    val text = cp(0x61, 0x0301, 0x0323)
    assertThat(presentedText(text)).isEqualTo(text)
  }

  @Test
  fun `ZWJ emoji sequence is shown`() {
    // U+1F468 ZWJ U+1F469 ZWJ U+1F467 (family): the U+200D joiners are preserved, so the sequence round-trips.
    val zwj = 0x200D
    val text = cp(0x1F468, zwj, 0x1F469, zwj, 0x1F467)
    assertThat(presentedText(text)).isEqualTo(text)
  }

  @Test
  fun `ZWJ profession sequence is shown`() {
    // U+1F9D1 ZWJ U+1F4BB (person + laptop).
    val text = cp(0x1F9D1, 0x200D, 0x1F4BB)
    assertThat(presentedText(text)).isEqualTo(text)
  }

  @Test
  fun `emoji with skin-tone modifier is shown`() {
    // U+1F44D + U+1F3FD (thumbs-up + medium skin tone).
    val text = cp(0x1F44D, 0x1F3FD)
    assertThat(presentedText(text)).isEqualTo(text)
  }

  /**
   * A long stack of combining marks on one base must neither crash nor lose the base character.
   * Ported from libvterm's `t/61screen_unicode.test` (MIT, © Paul Evans), which caps a cell at 5
   * combining code points; this engine keeps the whole cluster.
   */
  @Test
  fun `ten combining accents on one base are shown`() {
    val text = cp(0x65, 0x301, 0x302, 0x303, 0x304, 0x305, 0x306, 0x307, 0x308, 0x309, 0x30A)
    assertThat(presentedText(text)).isEqualTo(text)
  }

  /**
   * A cluster split across two [TerminalEmulator.write] calls must be joined into one cell: the second
   * write's combining marks attach to the base written by the first. Ported from libvterm's
   * `t/61screen_unicode.test`.
   */
  @Test
  fun `combining accents split across two writes join one cluster`() {
    val accents = cp(*IntArray(20) { 0x301 })
    var text = ""
    session(30, 2) { session ->
      session.write(cp(0x65) + accents)
      session.write(accents)
      text = session.screenLine(0).toStyledText().text
    }
    assertThat(text).isEqualTo(cp(0x65) + accents + accents)
  }

  @Test
  fun `regional-indicator flag is shown`() {
    // U+1F1FA + U+1F1F8 (US flag): the two regional indicators round-trip.
    val text = cp(0x1F1FA, 0x1F1F8)
    assertThat(presentedText(text)).isEqualTo(text)
  }
}
