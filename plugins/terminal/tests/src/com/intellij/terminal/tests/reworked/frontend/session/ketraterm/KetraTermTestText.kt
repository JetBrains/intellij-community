// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ketraterm

import com.intellij.terminal.frontend.session.ketraterm.KetraTermTerminalSession
import io.github.ketraterm.render.api.TerminalRenderCellFlags
import io.github.ketraterm.render.api.TerminalRenderClusterSink
import kotlinx.coroutines.flow.first

internal fun KetraTermTerminalSession.retainedText(): String = buildString {
  terminalSession.readRenderFrameForAbsoluteRange(0, Long.MAX_VALUE) { frame ->
    val cursor = frame.cursor
    val rows = maxOf(cursor.row + 1, (frame.outputEndAbsoluteRow - frame.discardedCount).coerceIn(1, frame.rows.toLong()).toInt())
    val codes = IntArray(frame.columns)
    val flags = IntArray(frame.columns)
    val attrs = LongArray(frame.columns)
    val clusters = arrayOfNulls<String>(frame.columns)
    val sink = TerminalRenderClusterSink { column, text -> clusters[column] = text }
    for (row in 0 until rows) {
      clusters.fill(null)
      frame.copyLine(row, codes, attrWords = attrs, flags = flags, clusterSink = sink)
      val wrapped = frame.lineWrapped(row)
      var end = frame.columns
      if (!wrapped) {
        while (end > 0 && flags[end - 1] and TerminalRenderCellFlags.EMPTY != 0) end--
        if (row == cursor.row) end = maxOf(end, cursor.column.coerceAtMost(frame.columns))
      }
      for (column in 0 until end) {
        if (flags[column] and (TerminalRenderCellFlags.WIDE_TRAILING or TerminalRenderCellFlags.WRAP_PADDING) != 0) continue
        val cluster = clusters[column]
        when {
          cluster != null -> append(cluster)
          codes[column] != 0 -> appendCodePoint(codes[column])
          else -> append(' ')
        }
      }
      if (!wrapped && row + 1 < rows) append('\n')
    }
  }
}

internal suspend fun KetraTermTerminalSession.awaitText(text: String) {
  terminalSession.renderGeneration.first { text in retainedText() }
}
