// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.jetbrains.annotations.ApiStatus

// The grid an embedder reads back: its size, what a cell holds (text, width, appearance, OSC8 hyperlinks).

/** Screen size in character cells. */
@ApiStatus.Internal
data class TerminalSize(val columns: Int, val rows: Int) {
  init {
    require(columns in 1..MAX_DIMENSION) { "columns must be in 1..$MAX_DIMENSION, was $columns" }
    require(rows in 1..MAX_DIMENSION) { "rows must be in 1..$MAX_DIMENSION, was $rows" }
  }

  companion object {
    /** Upper bound rows/columns: values must fit in an unsigned 16-bit integer. */
    const val MAX_DIMENSION: Int = 0xFFFF
  }
}

/** A foreground or background color. */
@ApiStatus.Internal
sealed interface TerminalColor {
  /** Defer to the embedder's configured default foreground or background. */
  data object Default : TerminalColor

  /**
   * One of the 16 base ANSI palette colors (`0..15`). The embedder resolves
   * these against its own theme.
   */
  data class IndexedAnsi(val index: Int) : TerminalColor {
    init {
      require(index in 0..15) { "ANSI palette index must be in 0..15, was $index" }
    }
  }

  /**
   * An extended xterm palette color (`16..255`: the 6×6×6 color cube + 24-step grayscale ramp).
   *
   * This stays a live reference rather than a resolved [Rgb]: resolve it using [TerminalEmulator.paletteColor].
   */
  data class IndexedExtended(val index: Int) : TerminalColor {
    init {
      require(index in 16..255) { "extended palette index must be in 16..255, was $index" }
    }
  }

  /** 24-bit true color, each component 0..255. */
  data class Rgb(val red: Int, val green: Int, val blue: Int) : TerminalColor {
    init {
      require(red in 0..255) { "red must be in 0..255, was $red" }
      require(green in 0..255) { "green must be in 0..255, was $green" }
      require(blue in 0..255) { "blue must be in 0..255, was $blue" }
    }
  }
}

/** Underline rendering for a cell (SGR 4:x). */
@ApiStatus.Internal
enum class Underline { NONE, SINGLE, DOUBLE, CURLY, DOTTED, DASHED }

/**
 * Visual attributes of a cell.
 *
 * The engine also tracks strikethrough, overline, and underline color; this model does not carry them yet.
 */
@ApiStatus.Internal
data class CellStyle(
  val foreground: TerminalColor = TerminalColor.Default,
  val background: TerminalColor = TerminalColor.Default,
  val bold: Boolean = false,
  val faint: Boolean = false,
  val italic: Boolean = false,
  val blink: Boolean = false,
  val inverse: Boolean = false,
  val hidden: Boolean = false,
  val underline: Underline = Underline.NONE,
) {
  companion object {
    val Default: CellStyle = CellStyle()
  }
}

/** Cell width, for CJK / emoji double-width handling. */
@ApiStatus.Internal
enum class CellWidth {
  /** Normal single-column cell. */
  NARROW,

  /** Leading column of a double-width glyph. */
  WIDE,

  /** Placeholder column trailing a [WIDE] cell; carries no glyph. */
  SPACER,
}

/** A single grid cell. */
@ApiStatus.Internal
data class Cell(
  /** Unicode code point; 0 for an empty cell. May be supplementary (e.g. an emoji). */
  val codePoint: Int,
  val width: CellWidth = CellWidth.NARROW,
  val style: CellStyle = CellStyle.Default,
  /** The OSC 8 hyperlink URI this cell belongs to, or null if it carries no hyperlink. */
  val hyperlink: String? = null,
  /**
   * The code points following [codePoint] in this cell's grapheme cluster — combining marks, ZWJ
   * sequence parts, variation selectors (e.g. a U+0301 combining acute accent after its base letter) —
   * or empty for a single-code-point cell.
   */
  val trailingCodePoints: List<Int> = emptyList(),
) {
  init {
    require(codePoint in 0..0x10FFFF && codePoint !in 0xD800..0xDFFF) {
      "codePoint must be 0 or a valid Unicode scalar value, was $codePoint"
    }
  }

  companion object {
    val Empty: Cell = Cell(0)
  }
}

/** One row of the grid. */
@ApiStatus.Internal
class TerminalRow(
  /** The row's cells, left to right. The row owns the list; it must not change after construction. */
  val cells: List<Cell>,
  /** True when this row soft-wrapped into the next (no hard line break). */
  val wrapped: Boolean = false,
) {
  /**
   * Returns the row projected to displayable text with its attributes as coalesced ranges. Trailing
   * empty cells are dropped; trailing written spaces are kept.
   *
   * Each cell contributes the UTF-16 chars of its [Cell.codePoint] followed by those of its
   * [Cell.trailingCodePoints], so a full grapheme cluster is surfaced. A [CellWidth.SPACER] cell is
   * skipped (its leading [CellWidth.WIDE] cell already produced the glyph), and an empty cell before
   * the last glyph becomes a single space.
   */
  fun toStyledText(): StyledText {
    val chars = StringBuilder()
    val styleRanges = RangeBuilder(::StyleRange)
    val hyperlinks = RangeBuilder(::HyperlinkRange)
    var writtenLength = 0
    for (cell in cells) {
      if (cell.width == CellWidth.SPACER) continue // the leading wide cell already produced the glyph
      val style = if (cell.codePoint == 0 || cell.style == CellStyle.Default) null else cell.style
      styleRanges.update(chars.length, style)
      hyperlinks.update(chars.length, cell.hyperlink)
      if (cell.codePoint == 0) {
        chars.append(' ')
        continue
      }
      chars.appendCodePoint(cell.codePoint)
      for (trailingCodePoint in cell.trailingCodePoints) {
        chars.appendCodePoint(trailingCodePoint)
      }
      writtenLength = chars.length
    }
    return StyledText(chars.substring(0, writtenLength), styleRanges.finish(writtenLength), hyperlinks.finish(writtenLength))
  }

  /** Coalesces per-cell attribute values into maximal ranges: a range stays open while the value repeats. */
  private class RangeBuilder<T : Any, R>(private val createRange: (Int, Int, T) -> R) {
    private val ranges = ArrayList<R>()
    private var rangeValue: T? = null
    private var rangeStartOffset = 0

    fun update(offset: Int, value: T?) {
      if (value != this.rangeValue) {
        endRange(offset)
        rangeValue = value
        rangeStartOffset = offset
      }
    }

    fun finish(end: Int): List<R> {
      endRange(end)
      return ranges
    }

    private fun endRange(rangeEndOffset: Int) {
      val value = rangeValue ?: return
      if (rangeEndOffset > rangeStartOffset) {
        ranges.add(createRange(rangeStartOffset, rangeEndOffset, value))
      }
      rangeValue = null
    }
  }
}

/**
 * A row's displayable [text] with its attributes as coalesced ranges: [styleRanges] covers every maximal
 * run of adjacent characters sharing one non-default [CellStyle], [hyperlinks] every maximal run belonging
 * to one OSC 8 link ([Cell.hyperlink]). The two lists are independent — a link may span differently styled
 * text and vice versa. Characters outside the ranges carry the default style and no link. Within each
 * list, the ranges are non-overlapping and in ascending offset order.
 */
@ApiStatus.Internal
class StyledText(val text: String, val styleRanges: List<StyleRange>, val hyperlinks: List<HyperlinkRange>)

/** A maximal run of characters sharing one non-default [style]; offsets are end-exclusive `char` indexes into [StyledText.text]. */
@ApiStatus.Internal
data class StyleRange(val startOffset: Int, val endOffset: Int, val style: CellStyle)

/** A maximal run of characters belonging to one OSC 8 link; offsets are end-exclusive `char` indexes into [StyledText.text]. */
@ApiStatus.Internal
data class HyperlinkRange(val startOffset: Int, val endOffset: Int, val uri: String)
