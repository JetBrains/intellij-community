// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Color

/**
 * Shared renderer for the aligned, code-styled "type diff" tooltips. Lays out one or more rows of [Cell]s as
 * monospace `<code>` lines, padding each column to a common width (with non-breaking spaces) so the cells line
 * up vertically across rows; incompatible cells are shown in red and structural delimiters are muted.
 *
 * Both [PyTypeDiff] (two-row signature/type diffs) and [PyMismatchTooltips] (the argument-vs-candidate
 * overload report) build their rows out of these cells and render them the same way, so the two displays share
 * one look — aligned columns wrapped in code spans.
 */
internal object PyTypeDiffGrid {
  enum class Kind { DELIM, VALUE, MISMATCH }

  /**
   * How a row's mismatched cells are emphasized. A two-type diff distinguishes the provided value from the
   * expected one — like an editor diff, the provided (top) row's incompatible parts are shown in red and the
   * expected (bottom) row's in green, each over a subtle matching background so they stand out. Displays without
   * that two-sided meaning (the overload report) use [PLAIN]: a bare red foreground and no background.
   */
  enum class MismatchStyle { PLAIN, PROVIDED, EXPECTED }

  /** A styled run of text inside a cell, used to color individual union members separately. */
  class Segment(@NlsSafe val text: String, val kind: Kind)

  /**
   * One cell of the grid. [text] is the styled main content (or, when [segments] is non-null, the cell renders
   * those individually-colored runs instead — used to highlight only the offending members of a union). [suffix]
   * is an always-muted trailer (a comma and any `/`/`*` separators) that stays attached to the value while the
   * column padding falls after it, so commas hug their values. The column width counts the content + [suffix].
   */
  class Cell(
    @NlsSafe val text: String,
    val kind: Kind,
    val alignRight: Boolean = false,
    @NlsSafe val suffix: String = "",
    val segments: List<Segment>? = null,
  )

  /** A structural delimiter such as `(`, `[`, `, ` or ` -> ` — rendered muted. */
  fun delim(@NlsSafe text: String): Cell = Cell(text, Kind.DELIM)

  /** A type/parameter value — rendered in red when [mismatch], otherwise in the default code color. */
  fun value(@NlsSafe text: String, mismatch: Boolean, alignRight: Boolean = false, @NlsSafe suffix: String = ""): Cell =
    Cell(text, if (mismatch) Kind.MISMATCH else Kind.VALUE, alignRight, suffix)

  /** A value cell whose content is a sequence of [segments] (e.g. a union with only some members highlighted). */
  fun segmented(segments: List<Segment>, alignRight: Boolean = false, @NlsSafe suffix: String = ""): Cell =
    Cell("", Kind.VALUE, alignRight, suffix, segments)

  fun segment(@NlsSafe text: String, mismatch: Boolean): Segment = Segment(text, if (mismatch) Kind.MISMATCH else Kind.VALUE)
  fun segmentDelim(@NlsSafe text: String): Segment = Segment(text, Kind.DELIM)

  /** Returns a copy of [cell] with [suffix] appended to its current suffix (used to attach a trailing comma). */
  fun withSuffix(cell: Cell, @NlsSafe suffix: String): Cell =
    Cell(cell.text, cell.kind, cell.alignRight, cell.suffix + suffix, cell.segments)

  private val EMPTY: Cell = Cell("", Kind.DELIM)

  private fun width(cell: Cell): Int =
    (cell.segments?.sumOf { it.text.length } ?: cell.text.length) + cell.suffix.length

  /**
   * Builds the tooltip HTML: an optional [headline] chunk above a grid of [rows] (each a list of cells; rows may
   * differ in length and are padded on the right). Each row is prefixed with its [labels] entry (e.g. `Expected:`)
   * in a leading table column, so the reader can tell the rows apart. Pass [HtmlChunk.empty] for no headline.
   */
  @NlsContexts.Tooltip
  fun tooltip(
    headline: HtmlChunk,
    rows: List<List<Cell>>,
    labels: List<@Nls String>,
    mismatchStyles: List<MismatchStyle> = emptyList(),
  ): @NlsContexts.Tooltip String {
    val widths = columnWidths(rows)
    // A <table> is a block element, so the headline sits on its own line above the aligned rows.
    return HtmlBuilder().append(headline).append(labeledTable(rows, widths, labels, mismatchStyles)).wrapWith("html").toString()
  }

  private fun columnWidths(rows: List<List<Cell>>): IntArray {
    val columnCount = rows.maxOfOrNull { it.size } ?: 0
    return IntArray(columnCount) { i -> rows.maxOf { row -> row.getOrNull(i)?.let { width(it) } ?: 0 } }
  }

  /** One row rendered as a single monospace `<code>` line, each cell padded to its column width. */
  private fun line(row: List<Cell>, widths: IntArray, mismatchStyle: MismatchStyle): HtmlChunk {
    val builder = HtmlBuilder()
    for (i in widths.indices) {
      val cell = row.getOrNull(i) ?: EMPTY
      val padCount = (widths[i] - width(cell)).coerceAtLeast(0)
      // A mismatched component with no text of its own is a "gap": the value is absent on this side (a missing
      // parameter, name, type or default). Paint the column position it would occupy with the mismatch background
      // so the missing component is visible rather than an invisible empty cell, keeping any trailing separator muted.
      if (cell.kind == Kind.MISMATCH && cell.text.isEmpty() && cell.segments == null) {
        builder.append(missingBlock(padCount, mismatchStyle))
        if (cell.suffix.isNotEmpty()) builder.append(styledText(cell.suffix, Kind.DELIM, mismatchStyle))
        continue
      }
      val pad = if (padCount == 0) HtmlChunk.empty() else HtmlChunk.nbsp(padCount)
      if (cell.alignRight) builder.append(pad).append(styled(cell, mismatchStyle))
      else builder.append(styled(cell, mismatchStyle)).append(pad)
    }
    return builder.toFragment().wrapWith(HtmlChunk.tag("code").style(CODE_LINE_STYLE))
  }

  // A diff row is one aligned line: wrapping it would break the column alignment, so it must stay on a single line
  // and the tooltip shows a horizontal scrollbar for a long signature instead. This overrides the platform tooltip
  // stylesheet's `code { overflow-wrap: anywhere; }`, which would otherwise break the row to fit the tooltip width.
  private const val CODE_LINE_STYLE = "white-space: nowrap; overflow-wrap: normal;"

  private fun labeledTable(rows: List<List<Cell>>, widths: IntArray, labels: List<@Nls String>, mismatchStyles: List<MismatchStyle>): HtmlChunk {
    val table = HtmlBuilder()
    rows.forEachIndexed { i, row ->
      table.append(HtmlChunk.tag("tr").children(
        HtmlChunk.tag("td").style(labelStyle).addText(labels.getOrElse(i) { "" }),
        HtmlChunk.tag("td").child(line(row, widths, mismatchStyles.getOrElse(i) { MismatchStyle.PLAIN })),
      ))
    }
    return table.toFragment().wrapWith("table")
  }

  private fun styled(cell: Cell, mismatchStyle: MismatchStyle): HtmlChunk {
    val main = if (cell.segments != null) {
      val builder = HtmlBuilder()
      cell.segments.forEach { builder.append(styledText(it.text, it.kind, mismatchStyle)) }
      builder.toFragment()
    }
    else {
      styledText(cell.text, cell.kind, mismatchStyle)
    }
    if (cell.suffix.isEmpty()) return main
    return HtmlBuilder().append(main).append(styledText(cell.suffix, Kind.DELIM, mismatchStyle)).toFragment()
  }

  private fun styledText(@NlsSafe text: String, kind: Kind, mismatchStyle: MismatchStyle): HtmlChunk {
    val chunk = HtmlChunk.text(text)
    return when (kind) {
      Kind.DELIM -> chunk.wrapWith(HtmlChunk.span().style(mutedStyle))
      Kind.VALUE -> chunk
      Kind.MISMATCH -> chunk.wrapWith(HtmlChunk.span().style(mismatchCss(mismatchStyle)))
    }
  }

  /** A background-only block [width] columns wide in the row's mismatch color, marking the position a component
   *  would occupy on the side that is missing it (a missing parameter, name, type or default). */
  private fun missingBlock(width: Int, mismatchStyle: MismatchStyle): HtmlChunk {
    if (width <= 0) return HtmlChunk.empty()
    return HtmlChunk.nbsp(width).wrapWith(HtmlChunk.span().style(missingCss(mismatchStyle)))
  }

  /** The CSS for a mismatched cell: a red foreground for a provided value, green for an expected one, each over a
   *  subtle matching background — except [MismatchStyle.PLAIN], which is the bare red foreground with no background. */
  private fun mismatchCss(mismatchStyle: MismatchStyle): String = when (mismatchStyle) {
    MismatchStyle.PLAIN -> foreground(errorForeground)
    MismatchStyle.PROVIDED -> foreground(errorForeground) + tint(errorForeground)
    MismatchStyle.EXPECTED -> foreground(successForeground) + tint(successForeground)
  }

  /** The background tint for a missing position — the same soft band a mismatched cell uses, shown on its own
   *  (there is no text) so the gap reads as red on the provided side and green on the expected side. */
  private fun missingCss(mismatchStyle: MismatchStyle): String = when (mismatchStyle) {
    MismatchStyle.PLAIN, MismatchStyle.PROVIDED -> tint(errorForeground)
    MismatchStyle.EXPECTED -> tint(successForeground)
  }

  private fun foreground(color: Color): String = "color: " + ColorUtil.toHtmlColor(color) + ";"

  /** A soft background highlight: mostly the tooltip background with a hint of [color] mixed in, so the band sits
   *  behind the colored text without overpowering it. */
  private fun tint(color: Color): String =
    " background-color: " + ColorUtil.toHtmlColor(ColorUtil.mix(color, UIUtil.getToolTipBackground(), HIGHLIGHT_BACKGROUND_BLEND)) + ";"

  /** How much of the tooltip background to mix into a highlight color for its soft background tint. */
  private const val HIGHLIGHT_BACKGROUND_BLEND = 0.82

  private val errorForeground: Color get() = NamedColorUtil.getErrorForeground()
  private val successForeground: Color get() = UIUtil.getLabelSuccessForeground()

  private val mutedStyle: String get() = "color: " + ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground()) + ";"
  // Row labels keep the default foreground color (not muted) so they read as headings, not greyed-out text.
  private val labelStyle: String get() = "padding: 0px 8px 0px 4px;"
}
