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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.CursorShapeDto
import org.jetbrains.plugins.terminal.session.impl.dto.MouseFormatDto
import org.jetbrains.plugins.terminal.session.impl.dto.MouseModeDto
import org.jetbrains.plugins.terminal.session.impl.dto.Osc8HyperlinkDto
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
 * Owns the incremental-emission state — the absolute logical index of the current screen top, advanced (or reset,
 * see [buildContentUpdate]) by each call — together with the [HistoryMark] used to measure how much history was
 * finalized since the last call; [close] releases the mark.
 *
 * Not thread-safe: it reads the emulator, so every call must be serialized with all other emulator access —
 * in practice, every call is made under [GhosttyTerminalSession]'s lock.
 */
@ApiStatus.Internal
@VisibleForTesting
class TerminalEmulatorOutputProjector(private val emulator: TerminalEmulator) {

  // Measures rows finalized into scrollback since the last buildContentUpdate call. Created with the
  // emulator; closed on teardown.
  private val historyMark: HistoryMark = emulator.markHistoryBoundary()

  // Absolute logical index of the current screen top (grows as lines scroll off into history, or resets to
  // 0 when buildContentUpdate can no longer track it exactly); the anchor for incremental content updates.
  private var screenTopLogical = 0L

  /**
   * True, while the history is replaced by the active screen alone: exact tracking was lost, and reading the
   * retained scrollback is deferred until the output stops. See buildContentUpdate.
   */
  var isHistoryReplaced: Boolean = false
    private set

  /**
   * Projects the buffer changes since the last call into a [TerminalContentUpdatedEvent], reading and emitting only
   * the changed tail — the scrollback lines finalized since the last call, followed by the active screen — instead
   * of the whole buffer. That is O(newlyScrolledLines + screenRows) rows per call rather than O(scrollbackRows),
   * which matters a lot for this backend where every row read crosses the FFI boundary. (Plus the rows of one
   * soft-wrapped line where it straddles the boundary, and one row read to detect that it does.)
   *
   * [TerminalContentUpdatedEvent.startLineLogicalIndex] grows as lines scroll off into history ([screenTopLogical]),
   * as long as [historyMark] can report exactly how many rows were finalized since the last call — which it
   * can as long as that count does not exceed what the scrollback currently retains.
   *
   * Two things end that: the count exceeding the retained scrollback, which makes the old numbering
   * unrecoverable, or one update finalizing more than [HISTORY_REPLACE_LINES] rows, where reading them all
   * costs more than the content is worth. Either way this reports the active screen alone at index `0` and
   * keeps doing that — cheap, bounded by the screen height — until an update finalizes *nothing*, meaning the
   * output stopped. It then reads the retained scrollback once and resumes exact tracking. Waiting for a full
   * stop, rather than for the output to merely slow down, keeps the one expensive read out of the burst.
   *
   * The trade-off: as long as anything keeps scrolling (output arrives faster than [OUTPUT_POLL_INTERVAL]),
   * the model holds only the visible screen, so steady output that never pauses keeps its scrollback hidden until it does.
   * But it should be an exceptional case when - even if it adds one line every 20ms, it is still 50 new lines a second.
   */
  fun buildContentUpdate(): TerminalContentUpdatedEvent {
    val screenRows = emulator.size.rows
    val curScrollbackRows = emulator.scrollbackRows
    // finalizedLineCount() also picks up rows a resize (reflow) finalized without a write.
    val finalizedSinceLastEmit = historyMark.finalizedLineCount()
    historyMark.reset()

    val canTrackExactly = finalizedSinceLastEmit in 0..curScrollbackRows
    // Nothing at all scrolled since the last update.
    val outputStopped = finalizedSinceLastEmit == 0
    val tooMuchToRead = finalizedSinceLastEmit > HISTORY_REPLACE_LINES

    // This update's window: where it starts in scrollback, and the absolute index that start maps to.
    var fromH: Int
    val startLogical: Long
    when {
      // The history is already replaced, and the output has not stopped: keep reporting the screen alone.
      isHistoryReplaced && !outputStopped -> {
        fromH = curScrollbackRows
        startLogical = 0L
      }
      // The output stopped: read the retained scrollback once and resume exact tracking.
      isHistoryReplaced -> {
        isHistoryReplaced = false
        fromH = 0
        startLogical = 0L
      }
      // Either the numbering is unrecoverable, or this window is simply too big to be worth reading.
      !canTrackExactly || tooMuchToRead -> {
        isHistoryReplaced = true
        fromH = curScrollbackRows
        startLogical = 0L
      }
      // Normal scenario: report the changed tail
      else -> {
        fromH = curScrollbackRows - finalizedSinceLastEmit
        startLogical = screenTopLogical
      }
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
    // already re-anchored above; this starts the next emit's window here.
    screenTopLogical = startLogical + completedLogicalLines(rows, newHistoryRows)

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
 * How many rows one update may finalize before the projector stops reading the history and reports the active
 * screen alone (see [TerminalEmulatorOutputProjector.buildContentUpdate]). Above this, reading the newly
 * finalized rows costs more than the content is worth: under a burst it is about to scroll out anyway.
 */
private const val HISTORY_REPLACE_LINES = 1000
