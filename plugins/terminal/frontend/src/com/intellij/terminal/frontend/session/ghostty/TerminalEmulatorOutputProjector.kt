// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session.ghostty

import com.intellij.terminal.emulator.CellStyle
import com.intellij.terminal.emulator.CursorShape
import com.intellij.terminal.emulator.HistoryMark
import com.intellij.terminal.emulator.MouseEncoding
import com.intellij.terminal.emulator.MouseProtocol
import com.intellij.terminal.emulator.TerminalColor
import com.intellij.terminal.emulator.TerminalEmulator
import com.intellij.terminal.emulator.TerminalRow
import com.intellij.terminal.emulator.Underline
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.CursorShapeDto
import org.jetbrains.plugins.terminal.session.impl.dto.Osc8HyperlinkDto
import org.jetbrains.plugins.terminal.session.impl.dto.MouseFormatDto
import org.jetbrains.plugins.terminal.session.impl.dto.MouseModeDto
import org.jetbrains.plugins.terminal.session.impl.dto.StyleRangeDto
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalColorDto
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalStateDto
import org.jetbrains.plugins.terminal.session.impl.dto.TextStyleDto
import org.jetbrains.plugins.terminal.session.impl.dto.TextStyleOptionDto

/**
 * Projects [TerminalEmulator] state into the [org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent]
 * DTO model for [GhosttyTerminalSession]: incremental content updates ([buildContentUpdate]), cursor positions
 * ([computeCursor]), and terminal-state snapshots ([buildState]).
 *
 * Owns the incremental-emission state — the absolute logical index of the current screen top and the boundary
 * of the not-yet-emitted history tail, both advanced by [buildContentUpdate] — together with the [HistoryMark]
 * backing them; [close] releases the mark.
 *
 * Not thread-safe: it reads the emulator, so every call must be serialized with all other emulator access —
 * in practice, every call is made under [GhosttyTerminalSession]'s lock.
 */
internal class TerminalEmulatorOutputProjector(private val emulator: TerminalEmulator) {

  // Tracks the finalized-history / active-screen boundary so buildContentUpdate can append exactly the
  // lines that scrolled off since the last emit — staying correct even after the scrollback cap starts
  // evicting, where a raw scrollbackRows delta plateaus. Created with the emulator; closed on teardown.
  private val historyMark: HistoryMark = emulator.markHistoryBoundary()

  // Absolute logical index of the current screen top (grows as lines scroll off into history); the anchor for
  // incremental content updates.
  private var screenTopLogical = 0L

  // scrollbackRows as of the last content emit; the fallback boundary of the not-yet-emitted history tail
  // for the degraded case where the finalized-row count was lost (see unreportedCountLost).
  private var emittedScrollbackRows = 0

  // Rows finalized into scrollback since the last content emit. Accumulated write-by-write
  // ([noteOutputWritten]) rather than read off the mark at emit time: the mark is a reference into the
  // bounded scrollback, and eviction is page-granular, so an anchor left alone across many writes can be
  // swept away long before the appended rows reach the retained-row count — silently shifting the line
  // accounting. Folding after every write means the anchor only ever spans one write.
  private var unreportedFinalizedRows = 0

  // Set when a single write finalized more rows than the whole retained scrollback, evicting the
  // write-scoped anchor: that write's count is unrecoverable, and buildContentUpdate degrades to the
  // scrollbackRows delta.
  private var unreportedCountLost = false

  /**
   * Projects the buffer changes since the last call into a [TerminalContentUpdatedEvent], reading and emitting only
   * the changed tail — the scrollback lines finalized since the last call, followed by the active screen — instead
   * of the whole buffer. That is O(newlyScrolledLines + screenRows) rows per call rather than O(scrollbackRows),
   * which matters a lot for this backend where every row read crosses the FFI boundary. (Plus the rows of one
   * soft-wrapped line where it straddles the boundary, and one row read to detect that it does.)
   *
   * [TerminalContentUpdatedEvent.startLineLogicalIndex] grows as lines scroll off into history ([screenTopLogical]).
   * The number of newly finalized rows is accumulated write-by-write from [historyMark] (see
   * [noteOutputWritten]), so it stays exact even after the scrollback cap starts evicting (where the raw
   * `scrollbackRows` delta plateaus) — including page-granular eviction sweeping past long-lived anchors.
   * When some counted rows were evicted before this emit, the count still keeps the logical indices right
   * (one line per lost row; their wrap flags are unrecoverable). The only residual gap versus the JediTerm
   * pipeline: a single [emulator] write scrolling past the whole retained scrollback evicts even the
   * write-scoped anchor ([HistoryMark.finalizedLineCount] returns `-1`), and we degrade to the
   * `scrollbackRows` delta, which then under-reports — those lines are gone with no way to recover.
   */
  fun buildContentUpdate(): TerminalContentUpdatedEvent {
    val screenRows = emulator.size.rows
    foldFinalizedRows() // a resize (reflow) can finalize rows without a write
    val curScrollbackRows = emulator.scrollbackRows
    // Rows finalized since the last emit whose content was evicted before this emit could report them:
    // the reportable tail starts below them, but the logical indices must still account for them —
    // approximated as one line per row, since their wrap flags are gone with them.
    var evictedUnreportedRows = 0
    var fromH = when {
      // The count for one write is unknown (see unreportedCountLost): degrade to the scrollbackRows
      // delta, which under-reports at the cap — those lines are gone with no way to recover.
      unreportedCountLost -> emittedScrollbackRows.coerceIn(0, curScrollbackRows)
      unreportedFinalizedRows > curScrollbackRows -> {
        evictedUnreportedRows = unreportedFinalizedRows - curScrollbackRows
        0
      }
      else -> curScrollbackRows - unreportedFinalizedRows
    }
    // A soft-wrapped logical line can straddle that boundary: its first rows were finalized by an earlier emit and
    // the rest only now. [startLineLogicalIndex] addresses whole logical lines, and the model replaces from the
    // start of that line, so a tail that began mid-line would truncate it to its last rows. Back up to the line's
    // first row and re-emit it whole, keeping the rows read on the way for the emit below — a row read is the
    // expensive part (every cell crosses the FFI boundary). Those extra rows are all continuations, so they add
    // nothing to the logical line counts below.
    //
    // Cost: one extra row read per emit for ordinary output, since the row before the boundary has to be examined.
    // A single line long enough to stay under the cursor for many frames is re-emitted on each of them — measured
    // at ~15x the characters (vs ~1.5x) for a 200 KB line with no newline at all, bounded by the scrollback cap.
    // The alternative is a truncated line in the document, so the re-emit wins.
    val backedUp = ArrayList<TerminalRow>()
    while (fromH > 0) {
      val row = emulator.scrollbackLine(fromH - 1)
      if (!row.wrapped) break
      backedUp.add(row)
      fromH--
    }
    backedUp.reverse()
    val newHistoryRows = curScrollbackRows - fromH
    val startLogical = screenTopLogical + evictedUnreportedRows

    // Read only the tail: the newly finalized history rows followed by the active screen.
    val rows = ArrayList<TerminalRow>(newHistoryRows + screenRows)
    rows.addAll(backedUp)
    for (h in fromH + backedUp.size until curScrollbackRows) rows.add(emulator.scrollbackLine(h))
    for (y in 0 until screenRows) rows.add(emulator.screenLine(y))
    val rowTexts = rows.map { it.toStyledText() }

    val lastNonEmpty = rowTexts.indexOfLast { it.text.isNotEmpty() }

    val text = StringBuilder()
    // Row-local attribute ranges shift to event-text offsets; a run continuing across a soft-wrapped row
    // boundary merges (the rows join with no separator, so the offsets touch), while the '\n' after a hard
    // line end breaks adjacency by construction.
    val styleRuns = ArrayList<Run<CellStyle>>()
    val linkRuns = ArrayList<Run<String>>()
    for (i in 0..lastNonEmpty) {
      val base = text.length
      text.append(rowTexts[i].text)
      for ((start, end, style) in rowTexts[i].styleRanges) {
        appendRun(styleRuns, base + start, base + end, style)
      }
      for ((start, end, uri) in rowTexts[i].hyperlinks) {
        appendRun(linkRuns, base + start, base + end, uri)
      }
      // A soft-wrapped row continues the same logical line, so no '\n' separator after it.
      if (i != lastNonEmpty && !rows[i].wrapped) {
        text.append('\n')
      }
    }

    // Cursor lives on the active screen, which begins at index newHistoryRows within `rows`.
    val cursor = emulator.cursor
    var line = (newHistoryRows + cursor.row).coerceIn(0, maxOf(0, rows.size - 1))
    var column = cursor.column
    while (line - 1 >= 0 && rows[line - 1].wrapped) {
      line--
      column += rowTexts[line].text.length
    }
    val cursorLine = startLogical + completedLogicalLines(rows, line)

    // The finalized history rows move the screen top forward by their logical-line count. The mark is
    // already re-anchored (foldFinalizedRows above); consuming the fold's bookkeeping starts the next
    // emit's window here.
    screenTopLogical = startLogical + completedLogicalLines(rows, newHistoryRows)
    emittedScrollbackRows = curScrollbackRows
    unreportedFinalizedRows = 0
    unreportedCountLost = false

    return TerminalContentUpdatedEvent(
      text = text.toString(),
      styles = styleRuns.map { StyleRangeDto(it.start.toLong(), it.end.toLong(), toTextStyleDto(it.value), ignoreContrastAdjustment = false) },
      startLineLogicalIndex = startLogical,
      cursorLogicalLineIndex = cursorLine,
      cursorColumnIndex = column,
      osc8Hyperlinks = linkRuns.map { Osc8HyperlinkDto(it.start.toLong(), it.end.toLong(), it.value) },
    )
  }

  /**
   * Must be called after every [emulator] write: folds the rows that write finalized into the pending
   * bookkeeping and re-anchors [historyMark], so the anchor never has to survive more than one write's
   * worth of page-granular eviction. One native read (plus a reset when rows were finalized) per write.
   */
  fun noteOutputWritten() {
    foldFinalizedRows()
  }

  /**
   * Whether so many rows were finalized into history since the last [buildContentUpdate] that further
   * output risks evicting them before they are ever emitted. The session checks this after every write
   * (after [noteOutputWritten]) and forces an early projection instead of waiting for the next regular
   * one.
   *
   * The threshold is a quarter of the retained scrollback, floored at [HISTORY_FLUSH_MIN_LINES] so
   * ordinary bursts keep coalescing. A quarter, not half: the cap trims in large chunks, so the
   * retained-row count oscillates roughly 25-30% below the value sampled here, and the backlog must
   * stay under that trough with headroom for one more write. A single write large enough to blow
   * through the headroom still loses content — the same content a projection after every single write
   * would lose ([foldFinalizedRows] keeps the line *accounting* exact even then).
   */
  fun isUnemittedHistoryEvictionImminent(): Boolean {
    // A lost count means unreported rows are already being evicted: project now before more is lost.
    return unreportedCountLost || unreportedFinalizedRows >= maxOf(HISTORY_FLUSH_MIN_LINES, emulator.scrollbackRows / 4)
  }

  /**
   * Folds the rows finalized since [historyMark] was last anchored into [unreportedFinalizedRows] and
   * re-anchors the mark at the current boundary.
   */
  private fun foldFinalizedRows() {
    val sinceAnchor = historyMark.finalizedLineCount()
    when {
      sinceAnchor < 0 -> unreportedCountLost = true // the anchor itself was evicted: this window's count is gone
      sinceAnchor > 0 -> unreportedFinalizedRows += sinceAnchor
      else -> return // nothing finalized: the anchor already sits at the boundary
    }
    historyMark.reset()
  }

  /**
   * The active-screen cursor as an absolute logical (line, column), consistent with [screenTopLogical]. Used for
   * cursor-only updates (no content change).
   *
   * Reads only the rows *above* the cursor: their wrap flags determine the logical-line index, and the text
   * lengths of the soft-wrapped run immediately above the cursor extend its logical column. Rows below the
   * cursor cannot affect either, so they are not read at all, and row text is built only for the wrapped run —
   * row reads and text building are the expensive parts here (every row read crosses the FFI boundary).
   */
  fun computeCursor(): Pair<Long, Int> {
    val cursor = emulator.cursor
    var line = cursor.row.coerceIn(0, maxOf(0, emulator.size.rows - 1))
    var column = cursor.column
    val rowsAbove = ArrayList<TerminalRow>(line)
    for (y in 0 until line) rowsAbove.add(emulator.screenLine(y))
    while (line - 1 >= 0 && rowsAbove[line - 1].wrapped) {
      line--
      column += rowsAbove[line].toStyledText().text.length
    }
    return screenTopLogical + completedLogicalLines(rowsAbove, line) to column
  }

  /** Logical lines completed by rows `[0, untilRow)`: each row that does not soft-wrap ends one. */
  private fun completedLogicalLines(rows: List<TerminalRow>, untilRow: Int): Long =
    (0 until untilRow).count { !rows[it].wrapped }.toLong()

  /**
   * A [TerminalStateDto] snapshot of the emulator's current modes. [isShellIntegrationEnabled] and
   * [currentDirectory] are supplied by the session — they are tracked by shell integration, not the emulator.
   */
  fun buildState(isShellIntegrationEnabled: Boolean, currentDirectory: String?): TerminalStateDto = TerminalStateDto(
    isCursorVisible = emulator.cursor.visible,
    cursorShape = toCursorShapeDto(emulator.cursorShape, emulator.cursorBlinking),
    mouseMode = toMouseModeDto(emulator.mouseProtocol),
    mouseFormat = toMouseFormatDto(emulator.mouseEncoding),
    isAlternateScreenBuffer = emulator.usingAlternateScreen,
    isApplicationArrowKeys = emulator.applicationCursorKeys,
    isApplicationKeypad = emulator.applicationKeypad,
    isAutoNewLine = false,
    isAltSendsEscape = false,
    isBracketedPasteMode = emulator.bracketedPaste,
    windowTitle = emulator.title,
    isShellIntegrationEnabled = isShellIntegrationEnabled,
    currentDirectory = currentDirectory,
  )

  /** Releases the [HistoryMark]. */
  fun close() {
    historyMark.close()
  }

  /** A `[start, end)` run of one attribute [value] in the event text; emulator-side until the final DTO mapping. */
  private class Run<T>(val start: Int, var end: Int, val value: T)

  /** Appends a range, extending the previous run instead when the two touch and carry the same value. */
  private fun <T> appendRun(runs: ArrayList<Run<T>>, start: Int, end: Int, value: T) {
    val last = runs.lastOrNull()
    if (last != null && last.end == start && last.value == value) {
      last.end = end
    }
    else {
      runs.add(Run(start, end, value))
    }
  }

  private fun toTextStyleDto(style: CellStyle): TextStyleDto {
    val options = buildList {
      if (style.bold) add(TextStyleOptionDto.BOLD)
      if (style.italic) add(TextStyleOptionDto.ITALIC)
      if (style.faint) add(TextStyleOptionDto.DIM)
      if (style.blink) add(TextStyleOptionDto.SLOW_BLINK)
      if (style.inverse) add(TextStyleOptionDto.INVERSE)
      if (style.hidden) add(TextStyleOptionDto.HIDDEN)
      if (style.underline != Underline.NONE) add(TextStyleOptionDto.UNDERLINED)
    }
    return TextStyleDto(toColorDto(style.foreground), toColorDto(style.background), options)
  }

  private fun toColorDto(color: TerminalColor): TerminalColorDto? = when (color) {
    TerminalColor.Default -> null
    // ANSI 0..15: ship the index; the frontend resolves it against its theme.
    is TerminalColor.IndexedAnsi -> TerminalColorDto(colorIndex = color.index, rgb = null)
    // Extended 16..255: a live palette reference, resolved here against the emulator's current palette.
    is TerminalColor.IndexedExtended -> toRgbDto(emulator.paletteColor(color.index))
    is TerminalColor.Rgb -> toRgbDto(color)
  }

  private fun toRgbDto(color: TerminalColor.Rgb): TerminalColorDto =
    TerminalColorDto(colorIndex = null, rgb = (color.red shl 16) or (color.green shl 8) or color.blue)

  /**
   * The [CursorShapeDto] folds the shape and the blink flag together, so combine the emulator's orthogonal
   * [CursorShape] and [TerminalEmulator.cursorBlinking].
   */
  private fun toCursorShapeDto(shape: CursorShape, blinking: Boolean): CursorShapeDto = when (shape) {
    CursorShape.BLOCK -> if (blinking) CursorShapeDto.BLINK_BLOCK else CursorShapeDto.STEADY_BLOCK
    CursorShape.UNDERLINE -> if (blinking) CursorShapeDto.BLINK_UNDERLINE else CursorShapeDto.STEADY_UNDERLINE
    CursorShape.BAR -> if (blinking) CursorShapeDto.BLINK_VERTICAL_BAR else CursorShapeDto.STEADY_VERTICAL_BAR
  }

  private fun toMouseModeDto(protocol: MouseProtocol): MouseModeDto = when (protocol) {
    MouseProtocol.NONE -> MouseModeDto.MOUSE_REPORTING_NONE
    MouseProtocol.X10 -> MouseModeDto.MOUSE_REPORTING_NORMAL
    MouseProtocol.NORMAL -> MouseModeDto.MOUSE_REPORTING_NORMAL
    MouseProtocol.BUTTON -> MouseModeDto.MOUSE_REPORTING_BUTTON_MOTION
    MouseProtocol.ANY -> MouseModeDto.MOUSE_REPORTING_ALL_MOTION
  }

  private fun toMouseFormatDto(encoding: MouseEncoding): MouseFormatDto = when (encoding) {
    MouseEncoding.DEFAULT -> MouseFormatDto.MOUSE_FORMAT_XTERM
    MouseEncoding.UTF8 -> MouseFormatDto.MOUSE_FORMAT_XTERM_EXT
    MouseEncoding.SGR, MouseEncoding.SGR_PIXELS -> MouseFormatDto.MOUSE_FORMAT_SGR
    MouseEncoding.URXVT -> MouseFormatDto.MOUSE_FORMAT_URXVT
  }
}

/**
 * The floor of the eviction-flush threshold (see [TerminalEmulatorOutputProjector.isUnemittedHistoryEvictionImminent]):
 * below this many unreported lines a forced projection never fires, so bursts keep coalescing even while the
 * scrollback is still small. Well under the rows the default ~1 MiB scrollback retains (about a thousand at
 * 80 columns).
 */
private const val HISTORY_FLUSH_MIN_LINES = 256
