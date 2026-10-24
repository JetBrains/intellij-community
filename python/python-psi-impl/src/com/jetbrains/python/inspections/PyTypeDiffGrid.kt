// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.ide.ui.ColorBlindness
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.documentation.PyDocumentationLink
import com.jetbrains.python.documentation.styledSpan
import com.jetbrains.python.highlighting.PyHighlighter
import com.jetbrains.python.psi.types.PyType
import org.jetbrains.annotations.Nls
import java.awt.Color

/**
 * Shared renderer for the aligned, code-styled "type diff" tooltips. Lays out one or more rows of [Cell]s as
 * monospace `<code>` lines, padding each column to a common width (with non-breaking spaces) so the cells line
 * up vertically across rows; incompatible cells are shown in red, and structural delimiters have their editor colours.
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
   *  rendering of a non-mismatched value run, used verbatim in place of [text]. [short], when set, is the short form
   *  that the grid shows in place of a long run (see [elide]). [parts], when set, are the runs that render [text], so
   *  that each name in a type such as `dict[str, Any]` keeps its own link. */
  class Segment(
    @NlsSafe val text: String,
    val kind: Kind,
    @NlsSafe val link: String? = null,
    val rich: HtmlChunk? = null,
    val short: List<Segment>? = null,
    val parts: List<Segment>? = null,
  )

  /**
   * One cell of the grid. [text] is the styled main content (or, when [segments] is non-null, the cell renders
   * those individually-colored runs instead — used to highlight only the offending members of a union). [suffix]
   * is a delimiter trailer (a comma and any `/`/`*` separators) that stays attached to the value while the
   * column padding falls after it, so commas hug their values. The column width counts the content + [suffix].
   * A cell with a [span] above 1 covers that many columns (see [spanning]). [short], when set, is the short form that
   * the grid shows in place of a long [text] (see [elide]). [parts], when set, are the runs that render [text] (see
   * [Segment.parts]).
   */
  class Cell(
    @NlsSafe val text: String,
    val kind: Kind,
    val alignRight: Boolean = false,
    @NlsSafe val suffix: String = "",
    val segments: List<Segment>? = null,
    @NlsSafe val link: String? = null,
    val rich: HtmlChunk? = null,
    val span: Int = 1,
    val short: List<Segment>? = null,
    val parts: List<Segment>? = null,
  )

  /** A structural delimiter such as `(`, `[`, `, ` or ` -> `. It has the colour that the editor gives it (see [delimiter]). */
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
   *  is what guarantees every rendered type gets its colour + link. [short] is the short form of a long [name], and
   *  [parts] are the runs that render [name] with a link for each name in it. */
  fun typeValue(type: PyType?, @NlsSafe name: String, mismatch: Boolean, alignRight: Boolean = false, @NlsSafe suffix: String = "",
                short: List<Segment>? = null, parts: List<Segment>? = null): Cell =
    Cell(name, if (mismatch) Kind.MISMATCH else Kind.VALUE, alignRight, suffix, link = elementLink(type), rich = richType(type, name, mismatch),
         short = short, parts = parts)

  /** Like [typeValue] but for one run inside a cell (a single union member). */
  fun typeSegment(type: PyType?, @NlsSafe name: String, mismatch: Boolean, short: List<Segment>? = null,
                  parts: List<Segment>? = null): Segment =
    Segment(name, if (mismatch) Kind.MISMATCH else Kind.VALUE, elementLink(type), richType(type, name, mismatch), short, parts)

  /** A delimiter run inside a type, such as `[` or `, `. It keeps the highlight of a [mismatch] type, so the
   *  highlight has no gap. */
  fun typeDelim(@NlsSafe text: String, mismatch: Boolean): Segment = Segment(text, if (mismatch) Kind.MISMATCH else Kind.DELIM)

  /** The platform's highlighted + linked rendering of [type] shown as [name], for a non-[mismatch] value (a
   *  mismatched value keeps the diff's own red/green, which must win over the syntax colour). */
  private fun richType(type: PyType?, @NlsSafe name: String, mismatch: Boolean): HtmlChunk? =
    if (mismatch) null else PyDocumentationLink.toTypeTooltipLink(type, name)

  /** A navigable `#element/<fqn>` href to [type]'s declaration (resolved by the platform ElementLinkHandler purely by
   *  qualified name), or null when the type has nothing to navigate to (see [PyDocumentationLink.tooltipLinkTarget]). */
  private fun elementLink(type: PyType?): String? = PyDocumentationLink.tooltipLinkTarget(type)

  /** Returns a copy of [cell] with [suffix] appended to its current suffix (used to attach a trailing comma). */
  fun withSuffix(cell: Cell, @NlsSafe suffix: String): Cell =
    Cell(cell.text, cell.kind, cell.alignRight, cell.suffix + suffix, cell.segments, cell.link, cell.rich, cell.span, cell.short, cell.parts)

  /** [cell] stretched over [span] columns, followed by a placeholder for each other column it covers. A spanning cell
   *  shows a value that does not line up with the columns of the other rows. */
  fun spanning(cell: Cell, span: Int): List<Cell> {
    if (span <= 1) return listOf(cell)
    return listOf(Cell(cell.text, cell.kind, cell.alignRight, cell.suffix, cell.segments, cell.link, cell.rich, span, cell.short, cell.parts)) +
           List(span - 1) { SPANNED }
  }

  /** Whether [cell] shows an incompatible part, in its main text or in one of its segments. */
  fun hasMismatch(cell: Cell?): Boolean =
    cell != null && (cell.kind == Kind.MISMATCH || cell.segments?.any { it.kind == Kind.MISMATCH } == true)

  /** The visible text of [cell], its suffix included. */
  @NlsSafe
  fun visibleText(cell: Cell): String = (cell.segments?.joinToString("") { it.text } ?: cell.text) + cell.suffix

  private val EMPTY: Cell = Cell("", Kind.DELIM)

  /** The placeholder for a column that a spanning cell covers. */
  private val SPANNED: Cell = Cell("", Kind.DELIM)

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
   *
   * [slots] are the column ranges of the elidable units of the rows: a parameter, a type argument, a union member.
   * Slots may nest. A grid wider than [LINE_WIDTH_BUDGET] elides its matched parts first (see [elide]). An optional
   * [footer] goes below the grid.
   */
  @NlsContexts.Tooltip
  fun tooltip(headline: HtmlChunk, rows: List<Row>, slots: List<IntRange> = emptyList(), footer: HtmlChunk = HtmlChunk.empty()): @NlsContexts.Tooltip String {
    val shownRows = elide(rows, slots)
    val widths = columnWidths(shownRows.map { it.cells })
    // A <table> is a block element, so the headline sits on its own line above the aligned rows.
    return HtmlBuilder().append(headline).append(labeledTable(shownRows, widths)).append(footer).wrapWith("html").toString()
  }

  // ---- Elision ------------------------------------------------------------------------------------------------

  /** The widest row, in characters, that the grid shows in full. A wider grid elides its matched parts. */
  private const val LINE_WIDTH_BUDGET = 120

  /** The length that a long text is shortened to, the `…` included. A text up to this length is never shortened. */
  const val SHORTENED_TEXT_LENGTH: Int = 20

  /** The mark for elided text. */
  @NlsSafe
  const val ELLIPSIS: String = "…"

  /** The visible separators between sibling slots, trimmed. Two matched siblings join one run only across these. */
  private val SLOT_SEPARATORS = setOf("", ",", "|", "&")

  /**
   * Elides a grid that is wider than [LINE_WIDTH_BUDGET], in steps, until it fits: first the runs of matched slots,
   * then long matched text, and last long mismatched text on an [Side.EXPECTED] row. The provided side keeps all of
   * its mismatched text.
   */
  private fun elide(rows: List<Row>, slots: List<IntRange>): List<Row> {
    if (lineWidth(rows) <= LINE_WIDTH_BUDGET) return rows
    val collapsed = collapseMatchedSlots(rows, slots)
    if (lineWidth(collapsed) <= LINE_WIDTH_BUDGET) return collapsed
    val shortMatched = collapsed.map { row -> Row(row.label, row.cells.map { shortened(it, mismatched = false) }, row.side) }
    if (lineWidth(shortMatched) <= LINE_WIDTH_BUDGET) return shortMatched
    return shortMatched.map { row ->
      if (row.side != Side.EXPECTED) row else Row(row.label, row.cells.map { shortened(it, mismatched = true) }, row.side)
    }
  }

  private fun lineWidth(rows: List<Row>): Int = columnWidths(rows.map { it.cells }).sum()

  /**
   * Replaces each run of matched sibling [slots] with one `…`. A slot is matched when no row has a mismatch in its
   * columns. Two matched siblings join one run when only separators such as `, ` or ` | ` lie between them. Only the
   * outermost matched slots are collapsed, so a matched slot inside a matched slot goes with it. Every row keeps its
   * columns: the `…` goes into the first column of the run, and the other columns of the run become empty.
   */
  private fun collapseMatchedSlots(rows: List<Row>, slots: List<IntRange>): List<Row> {
    val distinctSlots = slots.distinct()
    val matched = distinctSlots.filter { slot -> rows.none { row -> slot.any { hasMismatch(row.cells.getOrNull(it)) } } }
    val outermost = matched.filter { slot -> matched.none { it != slot && it.contains(slot) } }.sortedBy { it.first }
    if (outermost.isEmpty()) return rows

    fun parent(slot: IntRange): IntRange? =
      distinctSlots.filter { it != slot && it.contains(slot) }.minByOrNull { it.last - it.first }
    fun onlySeparatorsBetween(left: IntRange, right: IntRange): Boolean =
      (left.last + 1 until right.first).all { i ->
        rows.all { row ->
          val cell = row.cells.getOrNull(i)
          cell == null || cell.kind == Kind.DELIM && visibleText(cell).trim() in SLOT_SEPARATORS
        }
      }

    val runs = mutableListOf<IntRange>()
    for (slot in outermost) {
      val previous = runs.lastOrNull()
      if (previous != null && parent(previous) == parent(slot) && onlySeparatorsBetween(previous, slot)) {
        runs[runs.lastIndex] = previous.first..slot.last
      }
      else {
        runs.add(slot)
      }
    }

    val columnCount = rows.maxOf { it.cells.size }
    val cellsByRow = rows.map { row -> MutableList(columnCount) { row.cells.getOrNull(it) ?: EMPTY } }
    // A run gives no gain when it is not wider than the `…` that replaces it.
    val widths = columnWidths(cellsByRow)
    for (run in runs.filter { run -> run.sumOf { widths[it] } > ELLIPSIS.length }) {
      for (cells in cellsByRow) {
        // A spanning cell that covers more than the run owns these columns on this row, so the row keeps its cells.
        val first = cells[run.first]
        if (first === SPANNED || run.first + first.span - 1 > run.last) continue
        cells[run.first] = collapsedCell(cells.subList(run.first, run.last + 1))
        for (i in run.first + 1..run.last) cells[i] = EMPTY
      }
    }
    return rows.zip(cellsByRow) { row, cells -> Row(row.label, cells, row.side) }
  }

  /** The `…` that stands for a collapsed run of [cells] on one row. It keeps the trailing separator of the run, and
   *  it is empty when the row shows nothing in the run. */
  private fun collapsedCell(cells: List<Cell>): Cell {
    val last = cells.lastOrNull { width(it) > 0 } ?: return EMPTY
    return Cell(ELLIPSIS, Kind.DELIM, suffix = last.suffix)
  }

  /**
   * Shortens the long runs of [cell] that are [mismatched] (or matched, when false). A cell shortens run by run, so a
   * short union member keeps its colour and link next to a shortened one. A shortened member that repeats an earlier
   * one, such as a second `(…) -> Any`, is dropped together with the separator before it.
   */
  private fun shortened(cell: Cell, mismatched: Boolean): Cell {
    if (cell.kind == Kind.DELIM) return cell
    val segments = cell.segments ?: listOf(Segment(cell.text, cell.kind, cell.link, cell.rich, cell.short, cell.parts))
    val shortSegments = mutableListOf<Segment>()
    val shownShortForms = mutableSetOf<List<Pair<String, Kind>>>()
    var changed = false
    for (segment in segments) {
      val short = if ((segment.kind == Kind.MISMATCH) == mismatched) shortened(segment) else listOf(segment)
      if (short.singleOrNull() === segment) {
        shortSegments.add(segment)
        continue
      }
      changed = true
      if (shownShortForms.add(short.map { it.text to it.kind })) {
        shortSegments.addAll(short)
      }
      else if (shortSegments.lastOrNull()?.kind == Kind.DELIM) {
        shortSegments.removeAt(shortSegments.lastIndex)
      }
    }
    if (!changed) return cell
    return Cell("", Kind.VALUE, cell.alignRight, cell.suffix, shortSegments, span = cell.span)
  }

  /**
   * A long [segment] in its short form, such as `Name[…]` or `(…) -> R`, which keeps the links of its names. A segment
   * with no short form keeps its start, a `…` and its end, with its link. The end often tells two names apart, such as
   * `_id` and `_name`. A mismatched segment keeps its highlight over the whole short form.
   */
  private fun shortened(segment: Segment): List<Segment> {
    if (segment.kind == Kind.DELIM || segment.text.length <= SHORTENED_TEXT_LENGTH) return listOf(segment)
    val mismatch = segment.kind == Kind.MISMATCH
    segment.short?.let { short -> return if (mismatch) short.map { Segment(it.text, Kind.MISMATCH, it.link) } else short }
    val headLength = SHORTENED_TEXT_LENGTH / 2
    val tailLength = SHORTENED_TEXT_LENGTH - ELLIPSIS.length - headLength
    return listOf(
      Segment(segment.text.take(headLength), segment.kind, segment.link),
      Segment(ELLIPSIS, if (mismatch) Kind.MISMATCH else Kind.DELIM),
      Segment(segment.text.takeLast(tailLength), segment.kind, segment.link),
    )
  }

  private fun IntRange.contains(other: IntRange): Boolean = first <= other.first && other.last <= last

  private fun columnWidths(rows: List<List<Cell>>): IntArray {
    val columnCount = rows.maxOfOrNull { it.size } ?: 0
    val widths = IntArray(columnCount) { i -> rows.maxOf { row -> row.getOrNull(i)?.takeIf { it.span == 1 }?.let { width(it) } ?: 0 } }
    // A spanning cell needs only the sum of the columns it covers. When it is wider, its last column grows.
    for (row in rows) {
      row.forEachIndexed { i, cell ->
        if (cell.span > 1) {
          val last = minOf(i + cell.span, columnCount) - 1
          val covered = (i..last).sumOf { widths[it] }
          if (width(cell) > covered) widths[last] += width(cell) - covered
        }
      }
    }
    return widths
  }

  /** One row rendered as a single monospace `<code>` line, each cell padded to its column width. */
  private fun line(row: List<Cell>, widths: IntArray, side: Side): HtmlChunk {
    val builder = HtmlBuilder()
    var i = 0
    while (i < widths.size) {
      val cell = row.getOrNull(i) ?: EMPTY
      val span = cell.span.coerceIn(1, widths.size - i)
      val padCount = ((i until i + span).sumOf { widths[it] } - width(cell)).coerceAtLeast(0)
      i += span
      // A mismatched component with no text of its own is a "gap": the value is absent on this side (a missing
      // parameter, name, type or default). Paint the column position it would occupy with the mismatch background
      // so the missing component is visible rather than an invisible empty cell, keeping any trailing separator plain.
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
      cell.segments.forEach { builder.append(styled(it, side)) }
      builder.toFragment()
    }
    else {
      styled(Segment(cell.text, cell.kind, cell.link, cell.rich, parts = cell.parts), side)
    }
    if (cell.suffix.isEmpty()) return main
    return HtmlBuilder().append(main).append(styledText(cell.suffix, Kind.DELIM, side, null, null)).toFragment()
  }

  private fun styled(segment: Segment, side: Side): HtmlChunk {
    val parts = segment.parts ?: return styledText(segment.text, segment.kind, side, segment.link, segment.rich)
    val builder = HtmlBuilder()
    parts.forEach { builder.append(styled(it, side)) }
    return builder.toFragment()
  }

  private fun styledText(@NlsSafe text: String, kind: Kind, side: Side, link: String?, rich: HtmlChunk?): HtmlChunk {
    // A non-mismatched value uses the platform's highlighted + linked rendering when supplied (`None` in the keyword
    // colour, a builtin in the builtin colour, carrying its own `#element/…` link), returned verbatim. A mismatched
    // value keeps the diff's own red/green, which must win over any syntax colour.
    if (kind == Kind.VALUE && rich != null) return rich
    val styled = when (kind) {
      Kind.DELIM -> delimiter(text)
      Kind.VALUE -> HtmlChunk.text(text)
      Kind.MISMATCH -> HtmlChunk.text(text).wrapWith(HtmlChunk.span().style(mismatchCss(side)))
    }
    // A navigable `#element/…` link to the type's declaration (resolved by the platform ElementLinkHandler purely by
    // qualified name); the diff keeps its own red/green colour inside the link rather than the link colour.
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

  /**
   * Delimiter [text] in the colours that the editor gives its punctuation, so that a type reads like code. Only the `…`
   * of elided text is muted, so that it stands out as hidden text.
   */
  private fun delimiter(@NlsSafe text: String): HtmlChunk {
    val builder = HtmlBuilder()
    var start = 0
    while (start < text.length) {
      if (text.startsWith(ELLIPSIS, start)) {
        builder.append(HtmlChunk.text(ELLIPSIS).wrapWith(HtmlChunk.span().style(mutedStyle)))
        start += ELLIPSIS.length
        continue
      }
      val key = punctuationKey(text[start])
      var end = start + 1
      while (end < text.length && !text.startsWith(ELLIPSIS, end) && punctuationKey(text[end]) == key) end++
      @NlsSafe val run = text.substring(start, end)
      builder.append(if (key == null) HtmlChunk.text(run) else styledSpan(run, key))
      start = end
    }
    return builder.toFragment()
  }

  private fun punctuationKey(char: Char): TextAttributesKey? = when (char) {
    '(', ')' -> PyHighlighter.PY_PARENTHS
    '[', ']' -> PyHighlighter.PY_BRACKETS
    ',' -> PyHighlighter.PY_COMMA
    '.' -> PyHighlighter.PY_DOT
    '-', '>', '|', '&', '*', '/', '=' -> PyHighlighter.PY_OPERATION_SIGN
    else -> null
  }

  private val mutedStyle: String get() = "color: " + ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground()) + ";"
  // Row labels keep the default foreground color (not muted) so they read as headings, not greyed-out text. A label
  // must not wrap either: a wide row would otherwise squeeze the label column and break the label mid-word.
  private val labelStyle: String get() = "padding: 0px 8px 0px 4px; $CODE_LINE_STYLE"
}
