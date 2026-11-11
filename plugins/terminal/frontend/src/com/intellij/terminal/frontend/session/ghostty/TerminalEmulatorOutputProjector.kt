// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session.ghostty

import com.intellij.platform.eel.provider.asEelPath
import com.intellij.terminal.emulator.CellStyle
import com.intellij.terminal.emulator.CursorShape
import com.intellij.terminal.emulator.HistoryMark
import com.intellij.terminal.emulator.MouseEncoding
import com.intellij.terminal.emulator.MouseProtocol
import com.intellij.terminal.emulator.ScreenChange
import com.intellij.terminal.emulator.StyledText
import com.intellij.terminal.emulator.TerminalColor
import com.intellij.terminal.emulator.TerminalEmulator
import com.intellij.terminal.emulator.TerminalRow
import com.intellij.terminal.emulator.TerminalSize
import com.intellij.terminal.emulator.Underline
import com.intellij.terminal.frontend.view.typeahead.TerminalLogicalPosition
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
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
import java.nio.file.Path

/**
 * Projects [TerminalEmulator] state into the [org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent]
 * DTO model for [GhosttyTerminalSession]: incremental content updates ([buildContentUpdate]), cursor positions
 * ([computeCursor]), and terminal-state snapshots ([buildState]).
 *
 * Owns the incremental-emission state — the logical position of the current screen top, advanced (or reset,
 * see [rangeAfterHistory]) by each call, and the screen rows as of the last call — together with
 * the [HistoryMark] used to measure how much history was finalized since the last call; [close] releases the mark.
 *
 * On the alternate screen, [buildContentUpdate] and [computeCursor] report the active screen alone, at logical
 * index 0 — it has no scrollback of its own — and leave [historyMark] and the screen-top anchor untouched. Both
 * track the primary screen only, which receives no writes while the alternate screen is active, so they resume
 * exactly where they left off the instant it deactivates.
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

  // Logical position of the current screen top: the anchor for incremental content updates. Its line grows as lines
  // scroll off into history, or resets to 0 when buildContentUpdate can no longer track it exactly. Its column is
  // the text length of the line part in the scrollback, when that line straddles the screen top. Primary screen only.
  private var screenTop = TerminalLogicalPosition(0, 0)

  // The active screen as the last buildContentUpdate call reported it; null before the first call.
  private var lastScreen: ScreenSnapshot? = null

  // scrollbackRows and the emulator size as of the last primary-screen poll to detect scrollback erasing (CSI 3J).
  // Seeded at construction so the first poll never misfires.
  private var lastScrollbackRows = emulator.scrollbackRows
  private var lastEmulatorSize = emulator.size

  /**
   * True, while the history is replaced by the active screen alone: exact tracking was lost, and reading the
   * retained scrollback is deferred until the output stops. See [rangeAfterHistory].
   *
   * Only meaningful when the primary screen is active.
   */
  var isHistoryReplaced: Boolean = false
    private set

  /**
   * Projects the buffer changes since the last call into a [TerminalContentUpdatedEvent], reading and emitting only
   * the changed tail instead of the whole buffer: every row read crosses the FFI boundary. Three steps choose the
   * rows to read:
   * 1. [rangeAfterHistory]: the scrollback lines finalized since the last call, followed by the active screen.
   * 2. [narrowToChangedRows]: only the screen rows that [change] names, while the other rows hold what the last call
   *    reported.
   * 3. [extendToLineStart]: back to the first row of the logical line, because the model replaces whole lines.
   *
   * On the alternate screen things are much simpler: content is reported from index 0, and none of the
   * [HistoryMark]/[isHistoryReplaced] bookkeeping is read or written.
   */
  fun buildContentUpdate(change: ScreenChange): TerminalContentUpdatedEvent {
    val alternate = emulator.usingAlternateScreen
    val size = emulator.size
    val scrollbackRows = emulator.scrollbackRows
    val cursor = emulator.cursor
    val cursorRow = cursor.row.coerceIn(0, size.rows - 1)
    val previousScreen = lastScreen?.takeIf { it.isAlternate == alternate && it.size == size }

    val fullRange = if (alternate) UpdateRange(0, size.rows, 0L, rowsStayed = true) else rangeAfterHistory(scrollbackRows, size)
    val range = extendToLineStart(narrowToChangedRows(fullRange, change, previousScreen, cursorRow), scrollbackRows)
    val window = Window((range.firstRow until range.endRow).map { rowAt(it, scrollbackRows) }, range.firstLine)

    // The index of screen row 0 in the window: negative when the window starts below the screen top.
    val topIndex = scrollbackRows - range.firstRow
    val top = if (topIndex >= 0) window.positionOf(topIndex, 0) else screenTopOf(alternate)
    val cursorPosition = window.positionOf(topIndex + cursorRow, cursor.column)

    // The finalized history rows move the screen top forward by their logical-line count. The mark is
    // already re-anchored by rangeAfterHistory; this starts the next emit's window here. Primary screen only.
    if (!alternate) {
      screenTop = top
      lastScrollbackRows = scrollbackRows
      lastEmulatorSize = size
    }
    rememberScreenRows(alternate, size, previousScreen, window, topIndex)
    return toEvent(window, cursorPosition, top)
  }

  /**
   * The rows that an update of the primary screen reads before [narrowToChangedRows]: the scrollback lines finalized
   * since the last call, followed by the active screen. That is O(newlyScrolledLines + screenRows) rows per call
   * rather than O(scrollbackRows). Reads and re-anchors [historyMark].
   *
   * [UpdateRange.firstLine] grows as lines scroll off into history ([screenTop]), as long as [historyMark] can report
   * exactly how many rows were finalized since the last call. A growing resize can instead pull rows back out of
   * scrollback onto the screen, which [historyMark] reports as a *negative* count.
   *
   * Two things make [historyMark]'s count unusable, and reset [screenTop] to zero through
   * [isHistoryReplaced] — deferring the (expensive) scrollback read until the output stops, because what
   * was lost was the *ability* to read it cheaply, not the content itself:
   * 1. [historyMark] returning `null` (the marked boundary was itself evicted, so the old numbering is unrecoverable).
   * 2. One update finalizing more than [HISTORY_REPLACE_LINES] rows, where reading them all costs more than the content is worth.
   *
   * **The trade-off**: as long as anything keeps scrolling (output arrives faster than
   * [OUTPUT_POLL_INTERVAL]), the model holds only the visible screen until the output slows down to not
   * finalize any rows during a projection window, so steady output that never pauses keeps its scrollback
   * hidden until it stops. But it should be an exceptional case when - even if it adds one line every 20ms,
   * it is still 50 new lines a second.
   *
   * A third case resets [screenTop] to zero the same way but *without* [isHistoryReplaced]: the
   * scrollback being erased (`CSI 3 J`, what `clear` sends). [historyMark] cannot report that on its own —
   * it relocates its pin instead of evicting it, so the count reads as a misleadingly ordinary `0` — so this
   * instead notices [TerminalEmulator.scrollbackRows] drop to `0` with no resize to explain it.
   */
  private fun rangeAfterHistory(scrollbackRows: Int, size: TerminalSize): UpdateRange {
    val endRow = scrollbackRows + size.rows
    // finalizedLineCount() also picks up rows a resize (reflow) finalized, or un-finalized, without a write;
    // null means the marked boundary was evicted.
    val finalizedSinceLastEmit = historyMark.finalizedLineCount()
    historyMark.reset()

    // Nothing at all scrolled since the last update.
    val outputStopped = finalizedSinceLastEmit == 0

    // CSI 3J (erase saved lines, what `clear` sends alongside CSI 2J) frees the whole scrollback, but the
    // native pin relocates instead of reporting itself evicted, so finalizedLineCount() reads a
    // misleadingly ordinary 0 instead of null. Nothing else drops scrollbackRows to 0 without a resize.
    val resized = size != lastEmulatorSize
    val scrollbackErased = !resized && scrollbackRows == 0 && lastScrollbackRows > 0

    return when {
      // The history is already replaced, and the output has not stopped: keep reporting the screen alone.
      isHistoryReplaced && !outputStopped -> UpdateRange(scrollbackRows, endRow, 0L)
      // The output stopped: read the retained scrollback once and resume exact tracking.
      isHistoryReplaced -> {
        isHistoryReplaced = false
        UpdateRange(0, endRow, 0L)
      }
      // The scrollback is empty
      scrollbackErased -> UpdateRange(0, endRow, 0L)
      // The boundary was evicted, or this window is simply too big to be worth reading.
      finalizedSinceLastEmit == null || finalizedSinceLastEmit > HISTORY_REPLACE_LINES -> {
        isHistoryReplaced = true
        UpdateRange(scrollbackRows, endRow, 0L)
      }
      // A resize recovered rows from scrollback onto the screen instead of finalizing new ones: nothing new
      // to read from scrollback, but the anchor has to move back by however many logical lines that was.
      finalizedSinceLastEmit < 0 -> {
        val recoveredCount = (-finalizedSinceLastEmit).coerceAtMost(size.rows)
        val recoveredLines = (0 until recoveredCount).count { !emulator.isScreenLineWrapped(it) }
        UpdateRange(scrollbackRows, endRow, screenTop.lineIndex - recoveredLines)
      }
      // Normal scenario: report the changed tail
      else -> UpdateRange(scrollbackRows - finalizedSinceLastEmit, endRow, screenTop.lineIndex, rowsStayed = outputStopped)
    }
  }

  /**
   * [range] cut to the changed screen rows, so that typing reads and sends one line instead of the whole screen.
   * It starts at the first changed row, or at the cursor row when that is higher: the update takes the cursor
   * position from the rows it reads. It ends after the last changed row, the cursor row, and the last row with text.
   * The rows below them had no text in the last update, and they have none now.
   *
   * The other rows must hold what the last update reported. So [range] stays as it is when rows moved between the
   * scrollback and the screen, or when [previousScreen] is null. The engine reports the changed rows at their current
   * positions, and a row that changed and then scrolled off has no mark at all. The start also stops at a row whose
   * wrap flag changed: ghostty can change it without a mark (`Screen.cursorResetWrap`), and it moves each line end
   * below it.
   */
  private fun narrowToChangedRows(range: UpdateRange, change: ScreenChange, previousScreen: ScreenSnapshot?, cursorRow: Int): UpdateRange {
    if (!range.rowsStayed || change !is ScreenChange.Rows || previousScreen == null) return range
    val changedRows = change.rows
    val firstRow = (changedRows.minOrNull() ?: cursorRow).coerceIn(0, cursorRow)
    val lastRow = maxOf(changedRows.maxOrNull() ?: 0, cursorRow, previousScreen.hasText.lastIndexOf(true))
      .coerceAtMost(previousScreen.size.rows - 1)
    // The range starts at screen row 0 here.
    val topRow = range.firstRow
    var line = range.firstLine
    for (y in 0 until firstRow) {
      val wrapped = emulator.isScreenLineWrapped(y)
      if (wrapped != previousScreen.wrapped[y]) return UpdateRange(topRow + y, topRow + lastRow + 1, line)
      if (!wrapped) line++
    }
    return UpdateRange(topRow + firstRow, topRow + lastRow + 1, line)
  }

  /**
   * [range] moved up to the first row of its logical line. A soft-wrapped logical line can straddle the start of
   * [range]: its first rows were finalized by an earlier emit and the rest only now.
   * [TerminalContentUpdatedEvent.startLineLogicalIndex] addresses whole logical lines, and the model replaces from the
   * start of that line, so a range that began mid-line would truncate it to its last rows. The rows on the way are all
   * continuations, so [UpdateRange.firstLine] stays.
   *
   * Cost: one wrap-flag read per update, plus the rows of the line. A single line long enough to stay under the cursor
   * for many frames is re-emitted on each of them — measured at ~15x the characters (vs ~1.5x) for a 200 KB line with
   * no newline at all, bounded by the scrollback cap. The alternative is a truncated line in the document, so the
   * re-emit wins.
   */
  private fun extendToLineStart(range: UpdateRange, scrollbackRows: Int): UpdateRange {
    var firstRow = range.firstRow
    while (firstRow > 0 && wrapsAt(firstRow - 1, scrollbackRows)) firstRow--
    return range.copy(firstRow = firstRow)
  }

  /** Row [row] of the [scrollbackRows] scrollback rows followed by the screen rows. */
  private fun rowAt(row: Int, scrollbackRows: Int): TerminalRow =
    if (row < scrollbackRows) emulator.scrollbackLine(row) else emulator.screenLine(row - scrollbackRows)

  /** Whether row [row] of the [scrollbackRows] scrollback rows followed by the screen rows soft-wraps into the next one. */
  private fun wrapsAt(row: Int, scrollbackRows: Int): Boolean =
    if (row < scrollbackRows) emulator.isScrollbackLineWrapped(row) else emulator.isScreenLineWrapped(row - scrollbackRows)

  /**
   * Keeps the screen rows of [window] for the next [narrowToChangedRows]. The other rows keep what [previousScreen]
   * holds: the window holds each row that changed.
   */
  private fun rememberScreenRows(alternate: Boolean, size: TerminalSize, previousScreen: ScreenSnapshot?, window: Window, topIndex: Int) {
    val screen = previousScreen ?: ScreenSnapshot(alternate, size)
    for (y in maxOf(0, -topIndex) until minOf(size.rows, window.rows.size - topIndex)) {
      screen.wrapped[y] = window.rows[topIndex + y].wrapped
      screen.hasText[y] = window.texts[topIndex + y].text.isNotEmpty()
    }
    lastScreen = screen
  }

  /** The event for [window]: its text without the trailing blank rows, and the style and link ranges in that text. */
  private fun toEvent(window: Window, cursor: TerminalLogicalPosition, screenTop: TerminalLogicalPosition): TerminalContentUpdatedEvent {
    val lastNonEmpty = window.texts.indexOfLast { it.text.isNotEmpty() }
    val text = StringBuilder()
    // Row-local attribute ranges shift to event-text offsets; a run continuing across a soft-wrapped row
    // boundary merges (the rows join with no separator, so the offsets touch), while the '\n' after a hard
    // line end breaks adjacency by construction.
    val styleRuns = ArrayList<Run<CellStyle>>()
    val linkRuns = ArrayList<Run<String>>()
    for (i in 0..lastNonEmpty) {
      val base = text.length
      val rowText = window.texts[i]
      text.append(rowText.text)
      for ((start, end, style) in rowText.styleRanges) {
        appendRun(styleRuns, base + start, base + end, style)
      }
      for ((start, end, uri) in rowText.hyperlinks) {
        appendRun(linkRuns, base + start, base + end, uri)
      }
      // A soft-wrapped row continues the same logical line, so no '\n' separator after it.
      if (i != lastNonEmpty && !window.rows[i].wrapped) {
        text.append('\n')
      }
    }
    return TerminalContentUpdatedEvent(
      text = text.toString(),
      styles = styleRuns.map { toStyleRangeDto(it, text) },
      startLineLogicalIndex = window.firstLine,
      cursorLogicalLineIndex = cursor.lineIndex,
      cursorColumnIndex = cursor.columnIndex,
      screenTopLogicalLineIndex = screenTop.lineIndex,
      screenTopColumnIndex = screenTop.columnIndex,
      osc8Hyperlinks = linkRuns.map { Osc8HyperlinkDto(it.start.toLong(), it.end.toLong(), it.value) },
    )
  }

  /**
   * The active-screen cursor as an absolute logical (line, column), consistent with [screenTop] — or with
   * `0` on the alternate screen, which has no scrollback of its own. Used for cursor-only updates (no content
   * change).
   *
   * Reads the wrap flags of the rows above the cursor, and the cells of the cursor line only.
   */
  fun computeCursor(): Pair<Long, Int> {
    val cursor = emulator.cursor
    val cursorRow = cursor.row.coerceIn(0, emulator.size.rows - 1)
    val wrapped = BooleanArray(cursorRow) { emulator.isScreenLineWrapped(it) }
    var lineStart = cursorRow
    while (lineStart > 0 && wrapped[lineStart - 1]) lineStart--
    val top = screenTopOf(emulator.usingAlternateScreen)
    val cursorLine = Window(
      rows = (lineStart..cursorRow).map { emulator.screenLine(it) },
      firstLine = top.lineIndex + (0 until lineStart).count { !wrapped[it] },
      // A line that starts at the screen top can straddle it, so its scrollback part counts too.
      firstColumn = if (lineStart == 0) top.columnIndex else 0,
    )
    val position = cursorLine.positionOf(cursorRow - lineStart, cursor.column)
    return position.lineIndex to position.columnIndex
  }

  /** The logical position of screen row 0. The alternate screen has no scrollback, so its logical lines start at 0. */
  private fun screenTopOf(alternate: Boolean): TerminalLogicalPosition =
    if (alternate) TerminalLogicalPosition(0, 0) else screenTop

  /**
   * A [TerminalStateDto] snapshot of the emulator's current modes. [isShellIntegrationEnabled] and
   * [currentDirectory] are supplied by the session — they are tracked by shell integration, not the emulator.
   */
  fun buildState(isShellIntegrationEnabled: Boolean, currentDirectory: Path?): TerminalStateDto = TerminalStateDto(
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
    currentDirectory = currentDirectory?.asEelPath()?.toString(),
  )

  /** Releases the [HistoryMark]. */
  fun close() {
    historyMark.close()
  }

  /**
   * The rows that an update reads: [firstRow] until [endRow], counting the scrollback rows first, then the screen
   * rows. [firstLine] is the logical line that holds [firstRow]. [rowsStayed]: no row moved between the scrollback
   * and the screen since the last update.
   */
  private data class UpdateRange(val firstRow: Int, val endRow: Int, val firstLine: Long, val rowsStayed: Boolean = false)

  /**
   * Consecutive rows read from the emulator, with their text. The first row is in the logical line [firstLine], at
   * the char column [firstColumn] of that line.
   */
  private class Window(val rows: List<TerminalRow>, val firstLine: Long, val firstColumn: Int = 0) {
    val texts: List<StyledText> = rows.map { it.toStyledText() }

    /**
     * The logical position of the cell at the grid [column] of `rows[index]`.
     *
     * [TerminalRow.charOffsetOfColumn] compacts [column] to the offset [TerminalRow.toStyledText] would give it,
     * dropping the padding half of any double-width cell before it — using [column] itself here would land one
     * column too far right for every such cell. A soft-wrapped row continues the logical line above it, so this
     * backs up to the row that starts the line and extends the column by the text of every row it passes.
     */
    fun positionOf(index: Int, column: Int): TerminalLogicalPosition {
      var lineStart = index
      var charColumn = rows[index].charOffsetOfColumn(column)
      while (lineStart > 0 && rows[lineStart - 1].wrapped) {
        lineStart--
        charColumn += texts[lineStart].text.length
      }
      if (lineStart == 0) charColumn += firstColumn
      return TerminalLogicalPosition(firstLine + (0 until lineStart).count { !rows[it].wrapped }, charColumn)
    }
  }

  /** The screen rows as an update reported them: for each row, whether it soft-wraps and whether it has text. */
  private class ScreenSnapshot(val isAlternate: Boolean, val size: TerminalSize) {
    val wrapped = BooleanArray(size.rows)
    val hasText = BooleanArray(size.rows)
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

  private fun toStyleRangeDto(run: Run<CellStyle>, text: CharSequence): StyleRangeDto {
    val ignoreContrastAdjustment = (run.start until run.end).any {
      TerminalUiUtils.shouldIgnoreContrastAdjustment(text[it])
    }
    return StyleRangeDto(
      startOffset = run.start.toLong(),
      endOffset = run.end.toLong(),
      style = toTextStyleDto(run.value),
      ignoreContrastAdjustment = ignoreContrastAdjustment
    )
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
