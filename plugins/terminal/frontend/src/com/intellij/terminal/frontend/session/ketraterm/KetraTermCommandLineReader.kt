// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session.ketraterm

import io.github.ketraterm.render.api.TerminalRenderBufferKind
import io.github.ketraterm.render.api.TerminalRenderCellFlags
import io.github.ketraterm.render.api.TerminalRenderClusterSink
import io.github.ketraterm.render.api.TerminalRenderFrame
import io.github.ketraterm.session.TerminalShellCommandLineSnapshot

/** Extracts a bounded command only while its prompt anchor remains visible. */
internal class KetraTermCommandLineReader {
  private var lineId = 0L
  private var startColumn = 0
  private var previousSnapshot: TerminalShellCommandLineSnapshot? = null
  private var codes = IntArray(0)
  private var attrs = LongArray(0)
  private var flags = IntArray(0)
  private var clusters = arrayOfNulls<String>(0)
  private val clusterSink = TerminalRenderClusterSink { column, text -> clusters[column] = text }
  private val text = StringBuilder()

  fun setAnchor(frame: TerminalRenderFrame) {
    previousSnapshot = null
    lineId = frame.lineId(frame.cursor.row)
    startColumn = frame.cursor.column
  }

  fun read(frame: TerminalRenderFrame): TerminalShellCommandLineSnapshot? {
    if (lineId == 0L || frame.activeBuffer != TerminalRenderBufferKind.PRIMARY) return null
    val cursor = frame.cursor
    val anchorRow = (0 until frame.rows).firstOrNull { frame.lineId(it) == lineId } ?: return null
    if (cursor.row < anchorRow || cursor.row == anchorRow && cursor.column < startColumn) return null
    if (codes.size < frame.columns) {
      codes = IntArray(frame.columns)
      attrs = LongArray(frame.columns)
      flags = IntArray(frame.columns)
      clusters = arrayOfNulls(frame.columns)
    }
    text.setLength(0)
    var cursorOffset = -1
    var row = anchorRow
    while (row < frame.rows) {
      clusters.fill(null)
      frame.copyLine(row, codes, attrWords = attrs, flags = flags, clusterSink = clusterSink)
      val from = if (row == anchorRow) startColumn else 0
      var end = frame.columns
      if (!frame.lineWrapped(row)) {
        while (end > from && flags[end - 1] and TerminalRenderCellFlags.EMPTY != 0) end--
        if (row == cursor.row) end = maxOf(end, cursor.column)
      }
      for (column in from until end) {
        if (row == cursor.row && column == cursor.column) cursorOffset = text.length
        if (flags[column] and (TerminalRenderCellFlags.WIDE_TRAILING or TerminalRenderCellFlags.WRAP_PADDING) != 0) continue
        val cluster = clusters[column]
        if (cluster != null) text.append(cluster)
        else if (codes[column] != 0) text.appendCodePoint(codes[column])
        else text.append(' ')
        if (text.length > MAX_COMMAND_LENGTH) return null
      }
      if (row == cursor.row && cursor.column == end) cursorOffset = text.length
      if (!frame.lineWrapped(row)) {
        if (row >= cursor.row) break
        text.append('\n')
      }
      row++
    }
    if (cursorOffset < 0 || row == frame.rows) return null
    val previous = previousSnapshot
    if (previous != null && previous.cursorOffset == cursorOffset && previous.cursorColumn == cursor.column
        && previous.cursorRow == cursor.row && previous.commandText.contentEquals(text)) return previous
    return TerminalShellCommandLineSnapshot(text.toString(), cursorOffset, cursor.column, cursor.row).also { previousSnapshot = it }
  }

  companion object {
    private const val MAX_COMMAND_LENGTH = 4096
  }
}