// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session.ketraterm

import io.github.ketraterm.render.api.TerminalColorPalette
import io.github.ketraterm.render.api.TerminalRenderAttrs
import io.github.ketraterm.render.api.TerminalRenderBufferKind
import io.github.ketraterm.render.api.TerminalRenderCellFlags
import io.github.ketraterm.render.api.TerminalRenderClusterSink
import io.github.ketraterm.render.api.TerminalRenderColorKind
import io.github.ketraterm.render.api.TerminalRenderFrame
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCursorPositionChangedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.StyleRangeDto
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalColorDto
import org.jetbrains.plugins.terminal.session.impl.dto.TextStyleDto
import org.jetbrains.plugins.terminal.session.impl.dto.TextStyleOptionDto

/** Projects changed logical lines for IntelliJ APIs. Swing reads KetraTerm frames directly. */
internal class KetraTermOutputProjector {
  private val primary = BufferState()
  private val alternate = BufferState()
  private var codes = IntArray(0)
  private var attrs = LongArray(0)
  private var flags = IntArray(0)
  private var clusters = arrayOfNulls<String>(0)
  private val clusterSink = TerminalRenderClusterSink { column, text -> clusters[column] = text }
  private var lastBuffer: TerminalRenderBufferKind? = null
  private val text = StringBuilder()
  private val styleCache = HashMap<Long, TextStyleDto>()
  private var lastPalette: TerminalColorPalette? = null

  var cursor: TerminalCursorPositionChangedEvent = TerminalCursorPositionChangedEvent(0, 0)
    private set

  val hasPendingHistory: Boolean
    get() = lastBuffer == TerminalRenderBufferKind.PRIMARY && primary.pendingRow != Int.MAX_VALUE

  /**
   * The frame must cover all retained rows, including the live screen.
   * Prioritizes the visible tail when a large history update is pending. Logical line indices remain stable.
   * When supplied, [publishedFrameGeneration] prevents compatibility work from overtaking native frame publication.
   * The caller must complete pending history with [prioritizeVisible] disabled, including before shell events and termination.
   */
  fun project(
    frame: TerminalRenderFrame,
    force: Boolean = false,
    prioritizeVisible: Boolean = false,
    publishedFrameGeneration: Long? = null,
  ): TerminalContentUpdatedEvent? {
    if (publishedFrameGeneration != null && frame.frameGeneration != publishedFrameGeneration) return null
    val palette = frame.palette
    val paletteChanged = lastPalette !== palette
    if (paletteChanged) {
      styleCache.clear()
      lastPalette = palette
    }
    val state = if (frame.activeBuffer == TerminalRenderBufferKind.PRIMARY) primary else alternate
    val switched = lastBuffer != frame.activeBuffer
    lastBuffer = frame.activeBuffer
    val position = frame.cursor
    val liveTop = frame.historySize
    val cursorRow = position.row.coerceIn(0, frame.rows - 1)
    val count = maxOf(cursorRow + 1, (frame.outputEndAbsoluteRow - frame.discardedCount).coerceIn(1, frame.rows.toLong()).toInt())
    val reset = state.columns != frame.columns || state.historyGeneration != frame.historyContentGeneration
                || frame.discardedCount < state.discarded || frame.discardedCount - state.discarded > state.count
    if (reset) {
      state.baseLine = state.logicalLineForId(frame.lineId(0)) ?: 0
      state.count = 0
      state.discarded = frame.discardedCount
    }
    state.ensureCapacity(count)
    val dropped = (frame.discardedCount - state.discarded).coerceAtMost(state.count.toLong()).toInt()
    if (dropped > 0) {
      for (row in 0 until dropped) if (!state.wrapped[row]) state.baseLine++
      state.shift(dropped)
    }
    state.discarded = frame.discardedCount
    state.columns = frame.columns
    state.historyGeneration = frame.historyContentGeneration
    var changed = if (force || switched || reset || paletteChanged) 0 else minOf(state.count, count, state.pendingRow)
    if (reset || dropped > 0 || count != state.count || state.contentGeneration != frame.contentGeneration
        || state.frameGeneration != frame.frameGeneration) {
      for (row in 0 until count) {
        val id = frame.lineId(row)
        val generation = frame.lineGeneration(row)
        if (row >= state.count || state.ids[row] != id || state.generations[row] != generation) changed = minOf(changed, row)
        state.ids[row] = id
        state.generations[row] = generation
        state.wrapped[row] = frame.lineWrapped(row)
      }
    }
    state.contentGeneration = frame.contentGeneration
    state.frameGeneration = frame.frameGeneration
    if (count < state.count) changed = minOf(changed, count - 1)
    state.count = count
    if (changed < count) while (changed > 0 && state.wrapped[changed - 1]) changed--
    val reuseScreenTop = !reset && dropped == 0 && state.screenTopRow >= 0 && changed >= state.screenTopRow
                         && liveTop >= state.screenTopRow && cursorRow >= state.screenTopRow
    val scanStart = if (reuseScreenTop) state.screenTopRow else 0
    state.pendingRow = Int.MAX_VALUE
    if (prioritizeVisible && frame.activeBuffer == TerminalRenderBufferKind.PRIMARY && liveTop - changed > MAX_EAGER_HISTORY_ROWS) {
      var visibleStart = liveTop
      while (visibleStart > changed && state.wrapped[visibleStart - 1]) visibleStart--
      if (visibleStart > changed) {
        state.pendingRow = changed
        changed = visibleStart
      }
    }
    ensureRowCapacity(frame.columns)

    if (changed >= count) {
      var line = if (reuseScreenTop) state.screenTopLine else state.baseLine
      var column = if (reuseScreenTop) state.screenTopColumn else 0
      for (row in scanStart until cursorRow) {
        column += state.lengths[row]
        if (!state.wrapped[row]) {
          line++
          column = 0
        }
      }
      readRow(frame, cursorRow)
      updateCursor(line, column + textLength(position.column.coerceAtMost(frame.columns)))
      return null
    }
    text.setLength(0)
    val styles = ArrayList<StyleRangeDto>()
    if (styleCache.size > 1024) styleCache.clear()
    var logicalLine = if (reuseScreenTop) state.screenTopLine else state.baseLine
    var logicalColumn = if (reuseScreenTop) state.screenTopColumn else 0
    var startLine = logicalLine
    var screenLine = logicalLine
    var screenColumn = 0
    var cursorLine = logicalLine
    var cursorColumn = 0
    var runStart = 0
    var runAttribute = 0L
    fun flushStyle() {
      if (text.length > runStart && runAttribute != 0L) {
        styles.add(StyleRangeDto(runStart.toLong(), text.length.toLong(),
                                styleCache.getOrPut(runAttribute) { style(runAttribute, palette) }, false))
      }
      runStart = text.length
    }
    for (row in scanStart until count) {
      if (row == changed) startLine = logicalLine
      if (row == liveTop) {
        screenLine = logicalLine
        screenColumn = logicalColumn
      }
      if (row >= changed || row == cursorRow) {
        readRow(frame, row)
        if (row == cursorRow) {
          cursorLine = logicalLine
          cursorColumn = logicalColumn + textLength(position.column.coerceAtMost(frame.columns))
        }
      }
      if (row >= changed) {
        var endColumn = frame.columns
        if (!state.wrapped[row]) {
          while (endColumn > 0 && flags[endColumn - 1] and TerminalRenderCellFlags.EMPTY != 0) endColumn--
          if (row == cursorRow) endColumn = maxOf(endColumn, position.column.coerceAtMost(frame.columns))
        }
        val rowStart = text.length
        for (column in 0 until endColumn) {
          if (flags[column] and (TerminalRenderCellFlags.WIDE_TRAILING or TerminalRenderCellFlags.WRAP_PADDING) != 0) continue
          if (attrs[column] != runAttribute) {
            flushStyle()
            runAttribute = attrs[column]
          }
          appendCell(text, column)
        }
        state.lengths[row] = text.length - rowStart
        if (!state.wrapped[row] && row + 1 < count) {
          flushStyle()
          text.append('\n')
          runStart = text.length
          runAttribute = 0L
        }
      }
      logicalColumn += state.lengths[row]
      if (!state.wrapped[row]) {
        logicalLine++
        logicalColumn = 0
      }
    }
    updateCursor(cursorLine, cursorColumn)
    flushStyle()
    state.screenTopRow = liveTop
    state.screenTopLine = screenLine
    state.screenTopColumn = screenColumn
    return TerminalContentUpdatedEvent(
      text.toString(), styles, startLine, cursorLine, cursorColumn, screenLine, screenColumn,
    )
  }

  private fun updateCursor(line: Long, column: Int) {
    if (cursor.logicalLineIndex != line || cursor.columnIndex != column) {
      cursor = TerminalCursorPositionChangedEvent(line, column)
    }
  }

  private fun readRow(frame: TerminalRenderFrame, row: Int) {
    clusters.fill(null)
    frame.copyLine(row, codes, attrWords = attrs, flags = flags, clusterSink = clusterSink)
  }

  private fun textLength(columns: Int): Int {
    var result = 0
    for (column in 0 until columns) {
      if (flags[column] and (TerminalRenderCellFlags.WIDE_TRAILING or TerminalRenderCellFlags.WRAP_PADDING) != 0) continue
      result += clusters[column]?.length ?: if (codes[column] > 0xffff) 2 else 1
    }
    return result
  }

  private fun appendCell(text: StringBuilder, column: Int) {
    val cluster = clusters[column]
    if (cluster != null) text.append(cluster)
    else if (codes[column] != 0) text.appendCodePoint(codes[column])
    else text.append(' ')
  }

  private fun ensureRowCapacity(columns: Int) {
    if (codes.size >= columns) return
    codes = IntArray(columns)
    attrs = LongArray(columns)
    flags = IntArray(columns)
    clusters = arrayOfNulls(columns)
  }

  private fun style(word: Long, palette: TerminalColorPalette): TextStyleDto {
    val options = ArrayList<TextStyleOptionDto>(4)
    if (TerminalRenderAttrs.isBold(word)) options.add(TextStyleOptionDto.BOLD)
    if (TerminalRenderAttrs.isFaint(word)) options.add(TextStyleOptionDto.DIM)
    if (TerminalRenderAttrs.isItalic(word)) options.add(TextStyleOptionDto.ITALIC)
    if (TerminalRenderAttrs.isBlink(word)) options.add(TextStyleOptionDto.SLOW_BLINK)
    if (TerminalRenderAttrs.isInverse(word)) options.add(TextStyleOptionDto.INVERSE)
    if (TerminalRenderAttrs.isInvisible(word)) options.add(TextStyleOptionDto.HIDDEN)
    if (TerminalRenderAttrs.underlineStyle(word) != 0) options.add(TextStyleOptionDto.UNDERLINED)
    return TextStyleDto(
      color(TerminalRenderAttrs.foregroundKind(word), TerminalRenderAttrs.foregroundValue(word), palette),
      color(TerminalRenderAttrs.backgroundKind(word), TerminalRenderAttrs.backgroundValue(word), palette),
      options,
    )
  }

  private fun color(kind: Int, value: Int, palette: TerminalColorPalette): TerminalColorDto? = when (kind) {
    TerminalRenderColorKind.INDEXED -> {
      if (value < 16) TerminalColorDto(value, null)
      else TerminalColorDto(null, palette.indexedColor(value) and 0xFFFFFF)
    }
    TerminalRenderColorKind.RGB -> TerminalColorDto(null, value)
    else -> null
  }

  private class BufferState {
    var columns = 0
    var historyGeneration = -1L
    var contentGeneration = -1L
    var frameGeneration = -1L
    var discarded = 0L
    var baseLine = 0L
    var count = 0
    var screenTopRow = -1
    var screenTopLine = 0L
    var screenTopColumn = 0
    var pendingRow = Int.MAX_VALUE
    var ids = LongArray(0)
    var generations = LongArray(0)
    var wrapped = BooleanArray(0)
    var lengths = IntArray(0)

    fun logicalLineForId(id: Long): Long? {
      var line = baseLine
      for (row in 0 until count) {
        if (ids[row] == id) return line
        if (!wrapped[row]) line++
      }
      return null
    }

    fun ensureCapacity(size: Int) {
      if (ids.size >= size) return
      val capacity = maxOf(size, ids.size * 2, 64)
      ids = ids.copyOf(capacity)
      generations = generations.copyOf(capacity)
      wrapped = wrapped.copyOf(capacity)
      lengths = lengths.copyOf(capacity)
    }

    fun shift(rows: Int) {
      if (pendingRow != Int.MAX_VALUE) pendingRow = (pendingRow - rows).coerceAtLeast(0)
      ids.copyInto(ids, 0, rows, count)
      generations.copyInto(generations, 0, rows, count)
      wrapped.copyInto(wrapped, 0, rows, count)
      lengths.copyInto(lengths, 0, rows, count)
      count -= rows
    }
  }
}

private const val MAX_EAGER_HISTORY_ROWS = 512
