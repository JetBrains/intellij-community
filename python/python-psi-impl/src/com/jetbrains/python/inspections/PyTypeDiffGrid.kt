// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.ide.ui.ColorBlindness
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.documentation.PyDocumentationLink
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
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
   * Which side of the comparison a row shows. Like an editor diff, a [PROVIDED] row's incompatible cells are red
   * and an [EXPECTED] row's are green, each over a subtle matching background so they stand out. Every consumer
   * tags each of its rows with a side (see [Row]), so all displays — the two-row structural diff and the overload
   * report's argument-vs-candidate rows — colour their mismatches identically.
   */
  enum class Side { PROVIDED, EXPECTED }

  /** A styled run of text inside a cell, used to color individual union members separately. [link], when set, is a
   *  navigable `#element/…` href wrapped around this run. [rich], when set, is the platform's highlighted + linked
   *  rendering of a non-mismatched value run, used verbatim in place of [text]. */
  class Segment(@NlsSafe val text: String, val kind: Kind, @NlsSafe val link: String? = null, val rich: HtmlChunk? = null)

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
    @NlsSafe val link: String? = null,
    val rich: HtmlChunk? = null,
  )

  /** A structural delimiter such as `(`, `[`, `, ` or ` -> ` — rendered muted. */
  fun delim(@NlsSafe text: String): Cell = Cell(text, Kind.DELIM)

  /** A NON-type value cell — a parameter name, a `= ...` default, a `keyword=`: plain text, red when [mismatch],
   *  but NEVER a syntax colour or navigable link. A type must go through [typeValue] instead, which is the only way
   *  to attach a link + the platform's highlighted rendering — so a type can never be shown as a bare string. */
  fun value(@NlsSafe text: String, mismatch: Boolean, alignRight: Boolean = false, @NlsSafe suffix: String = ""): Cell =
    Cell(text, if (mismatch) Kind.MISMATCH else Kind.VALUE, alignRight, suffix)

  /** A value cell whose content is a sequence of [segments] (e.g. a union with only some members highlighted). */
  fun segmented(segments: List<Segment>, alignRight: Boolean = false, @NlsSafe suffix: String = ""): Cell =
    Cell("", Kind.VALUE, alignRight, suffix, segments)

  /** A NON-type segment run; a type member must go through [typeSegment] to carry its colour + link. */
  fun segment(@NlsSafe text: String, mismatch: Boolean): Segment = Segment(text, if (mismatch) Kind.MISMATCH else Kind.VALUE)
  fun segmentDelim(@NlsSafe text: String): Segment = Segment(text, Kind.DELIM)

  /** Turns a [type] (shown as [name]) into a value cell — THE single place every consumer (the structural diff and
   *  the overload report) turns a type into a cell, so type names render identically everywhere: red when [mismatch],
   *  otherwise the platform's highlighted, navigable rendering. Requiring the [type] here (rather than a bare name)
   *  is what guarantees every rendered type gets its colour + link. */
  fun typeValue(type: PyType?, @NlsSafe name: String, mismatch: Boolean, alignRight: Boolean = false, @NlsSafe suffix: String = ""): Cell =
    Cell(name, if (mismatch) Kind.MISMATCH else Kind.VALUE, alignRight, suffix, link = elementLink(type), rich = richType(type, name, mismatch))

  /** Like [typeValue] but for one run inside a cell (a single union member). */
  fun typeSegment(type: PyType?, @NlsSafe name: String, mismatch: Boolean): Segment =
    Segment(name, if (mismatch) Kind.MISMATCH else Kind.VALUE, elementLink(type), richType(type, name, mismatch))

  /** The platform's highlighted + linked rendering of [type] shown as [name], for a non-[mismatch] value (a
   *  mismatched value keeps the diff's own red/green, which must win over the syntax colour). */
  private fun richType(type: PyType?, @NlsSafe name: String, mismatch: Boolean): HtmlChunk? =
    if (mismatch) null else PyDocumentationLink.toTypeTooltipLink(type, name)

  /** A navigable `#element/<fqn>` href to [type]'s class declaration (resolved by the platform ElementLinkHandler
   *  purely by qualified name), or null for a non-class type. */
  private fun elementLink(type: PyType?): String? =
    (type as? PyClassType)?.pyClass?.qualifiedName?.let { PyDocumentationLink.TOOLTIP_ELEMENT_LINK_PREFIX + it }

  /** Returns a copy of [cell] with [suffix] appended to its current suffix (used to attach a trailing comma). */
  fun withSuffix(cell: Cell, @NlsSafe suffix: String): Cell =
    Cell(cell.text, cell.kind, cell.alignRight, cell.suffix + suffix, cell.segments, cell.link, cell.rich)

  private val EMPTY: Cell = Cell("", Kind.DELIM)

  private fun width(cell: Cell): Int =
    (cell.segments?.sumOf { it.text.length } ?: cell.text.length) + cell.suffix.length

  /** One labeled row of the grid: its [label] (e.g. `Expected:`; "" to continue under the previous row's label),
   *  the aligned [cells] of the line, and which [side] of the comparison it shows. Bundling the side WITH the cells
   *  is what keeps every consumer's colouring correct — a row can't be rendered without saying which side it is. */
  class Row(@Nls val label: String, val cells: List<Cell>, val side: Side)

  /**
   * Builds the tooltip HTML: an optional [headline] chunk above a grid of [rows]. Each [Row] carries its own label
   * (shown in a leading table column so the reader can tell the rows apart) and its [Side] (which colours its
   * mismatches); rows may differ in cell count and are padded on the right. Pass [HtmlChunk.empty] for no headline.
   */
  @NlsContexts.Tooltip
  fun tooltip(headline: HtmlChunk, rows: List<Row>): @NlsContexts.Tooltip String {
    val widths = columnWidths(rows.map { it.cells })
    // A <table> is a block element, so the headline sits on its own line above the aligned rows.
    return HtmlBuilder().append(headline).append(labeledTable(rows, widths)).wrapWith("html").toString()
  }

  private fun columnWidths(rows: List<List<Cell>>): IntArray {
    val columnCount = rows.maxOfOrNull { it.size } ?: 0
    return IntArray(columnCount) { i -> rows.maxOf { row -> row.getOrNull(i)?.let { width(it) } ?: 0 } }
  }

  /** One row rendered as a single monospace `<code>` line, each cell padded to its column width. */
  private fun line(row: List<Cell>, widths: IntArray, side: Side): HtmlChunk {
    val builder = HtmlBuilder()
    for (i in widths.indices) {
      val cell = row.getOrNull(i) ?: EMPTY
      val padCount = (widths[i] - width(cell)).coerceAtLeast(0)
      // A mismatched component with no text of its own is a "gap": the value is absent on this side (a missing
      // parameter, name, type or default). Paint the column position it would occupy with the mismatch background
      // so the missing component is visible rather than an invisible empty cell, keeping any trailing separator muted.
      if (cell.kind == Kind.MISMATCH && cell.text.isEmpty() && cell.segments == null) {
        builder.append(missingBlock(padCount, side))
        if (cell.suffix.isNotEmpty()) builder.append(styledText(cell.suffix, Kind.DELIM, side, null, null))
        continue
      }
      val pad = if (padCount == 0) HtmlChunk.empty() else HtmlChunk.nbsp(padCount)
      if (cell.alignRight) builder.append(pad).append(styled(cell, side))
      else builder.append(styled(cell, side)).append(pad)
    }
    return builder.toFragment().wrapWith(HtmlChunk.tag("code").style(CODE_LINE_STYLE))
  }

  // A diff row is one aligned line: wrapping it would break the column alignment, so it must stay on a single line
  // and the tooltip shows a horizontal scrollbar for a long signature instead. This overrides the platform tooltip
  // stylesheet's `code { overflow-wrap: anywhere; }`, which would otherwise break the row to fit the tooltip width.
  private const val CODE_LINE_STYLE = "white-space: nowrap; overflow-wrap: normal;"

  private fun labeledTable(rows: List<Row>, widths: IntArray): HtmlChunk {
    val table = HtmlBuilder()
    rows.forEach { row ->
      table.append(HtmlChunk.tag("tr").children(
        HtmlChunk.tag("td").style(labelStyle).addText(row.label),
        HtmlChunk.tag("td").child(line(row.cells, widths, row.side)),
      ))
    }
    return table.toFragment().wrapWith("table")
  }

  private fun styled(cell: Cell, side: Side): HtmlChunk {
    val main = if (cell.segments != null) {
      val builder = HtmlBuilder()
      cell.segments.forEach { builder.append(styledText(it.text, it.kind, side, it.link, it.rich)) }
      builder.toFragment()
    }
    else {
      styledText(cell.text, cell.kind, side, cell.link, cell.rich)
    }
    if (cell.suffix.isEmpty()) return main
    return HtmlBuilder().append(main).append(styledText(cell.suffix, Kind.DELIM, side, null, null)).toFragment()
  }

  private fun styledText(@NlsSafe text: String, kind: Kind, side: Side, link: String?, rich: HtmlChunk?): HtmlChunk {
    // A non-mismatched value uses the platform's highlighted + linked rendering when supplied (`None` in the keyword
    // colour, a builtin in the builtin colour, carrying its own `#element/…` link), returned verbatim. A mismatched
    // value keeps the diff's own red/green, which must win over any syntax colour.
    if (kind == Kind.VALUE && rich != null) return rich
    val styled = when (kind) {
      Kind.DELIM -> HtmlChunk.text(text).wrapWith(HtmlChunk.span().style(mutedStyle))
      Kind.VALUE -> HtmlChunk.text(text)
      Kind.MISMATCH -> HtmlChunk.text(text).wrapWith(HtmlChunk.span().style(mismatchCss(side)))
    }
    // A navigable `#element/…` link to the type's declaration (resolved by the platform ElementLinkHandler purely by
    // qualified name); the diff keeps its own red/green/muted colour inside the link rather than the link colour.
    return if (link == null) styled else HtmlChunk.tag("a").attr("href", link).child(styled)
  }

  /** A background-only block [width] columns wide in the row's mismatch color, marking the position a component
   *  would occupy on the side that is missing it (a missing parameter, name, type or default). */
  private fun missingBlock(width: Int, side: Side): HtmlChunk {
    if (width <= 0) return HtmlChunk.empty()
    return HtmlChunk.nbsp(width).wrapWith(HtmlChunk.span().style(missingCss(side)))
  }

  /** The CSS for a mismatched cell: a red foreground for a provided value, green for an expected one, each over a
   *  subtle matching background. */
  private fun mismatchCss(side: Side): String = when (side) {
    Side.PROVIDED -> foreground(errorForeground) + tint(errorForeground)
    Side.EXPECTED -> foreground(successForeground) + tint(successForeground)
  }

  /** The background tint for a missing position — the same soft band a mismatched cell uses, shown on its own
   *  (there is no text) so the gap reads as red on the provided side and green on the expected side. */
  private fun missingCss(side: Side): String = when (side) {
    Side.PROVIDED -> tint(errorForeground)
    Side.EXPECTED -> tint(successForeground)
  }

  private fun foreground(color: Color): String = "color: " + ColorUtil.toHtmlColor(color) + ";"

  /** A soft background highlight: mostly the tooltip background with a hint of [color] mixed in, so the band sits
   *  behind the colored text without overpowering it. */
  private fun tint(color: Color): String =
    " background-color: " + ColorUtil.toHtmlColor(ColorUtil.mix(color, UIUtil.getToolTipBackground(), HIGHLIGHT_BACKGROUND_BLEND)) + ";"

  /** How much of the tooltip background to mix into a highlight color for its soft background tint. */
  private const val HIGHLIGHT_BACKGROUND_BLEND = 0.82

  // Like an editor diff, the provided side is red and the expected side green — but red+green is exactly the pair
  // red-green colour-vision deficiency (protanopia/deuteranopia) can't separate, and the platform's daltonization
  // filter only corrects painted components/icons, not the colours we emit in HTML tooltips. So when the IDE's
  // colour-blindness setting is one of those, switch to an orange/blue pair that stays distinguishable.
  private val redGreenColorBlind: Boolean
    get() {
      val blindness = UISettings.getInstance().colorBlindness
      return blindness == ColorBlindness.protanopia || blindness == ColorBlindness.deuteranopia
    }

  private val errorForeground: Color
    get() = if (redGreenColorBlind) CVD_PROVIDED else NamedColorUtil.getErrorForeground()
  private val successForeground: Color
    get() = if (redGreenColorBlind) CVD_EXPECTED else UIUtil.getLabelSuccessForeground()

  // The colour-blind-safe replacements for red/green: orange (provided) and blue (expected), with light/dark variants.
  private val CVD_PROVIDED: Color = JBColor(Color(0xB5570C), Color(0xCC7832))
  private val CVD_EXPECTED: Color = JBColor(Color(0x256BB0), Color(0x4F9DF5))

  private val mutedStyle: String get() = "color: " + ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground()) + ";"
  // Row labels keep the default foreground color (not muted) so they read as headings, not greyed-out text.
  private val labelStyle: String get() = "padding: 0px 8px 0px 4px;"
}
