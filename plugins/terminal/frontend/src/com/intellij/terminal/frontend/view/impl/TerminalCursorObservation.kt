// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.impl

import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.ketraterm.KetraTermTerminalView
import com.intellij.terminal.frontend.session.ketraterm.KetraTermTerminalSession
import com.intellij.util.asDisposable
import io.github.ketraterm.render.api.TerminalRenderBufferKind
import io.github.ketraterm.render.api.TerminalRenderCellFlags
import io.github.ketraterm.render.api.TerminalRenderClusterSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import java.awt.event.KeyEvent

/** Reads cursor activity on the EDT. Line identifiers are opaque and local to the view. */
internal class TerminalCursorObservation(private val view: TerminalView, scope: CoroutineScope) {
  data class Line(val id: Long, val prefix: String)

  private val nativeView = view as? KetraTermTerminalView
  val keys: Flow<KeyEvent> = nativeView?.nativeKeyEvents ?: view.keyEventsFlow.map { it.awtEvent }
  val changes: Flow<Unit> = if (nativeView != null) flow {
    val session = view.sessionDeferred.await() as KetraTermTerminalSession
    emitAll(session.terminalSession.renderGeneration.map { Unit })
  }
  else MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).also { changes ->
    view.outputModels.regular.addListener(scope.asDisposable(), object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) { changes.tryEmit(Unit) }
      override fun cursorOffsetChanged(event: org.jetbrains.plugins.terminal.view.TerminalCursorOffsetChangeEvent) { changes.tryEmit(Unit) }
    })
  }

  private var codes = IntArray(0)
  private var flags = IntArray(0)
  private var attrs = LongArray(0)
  private var clusters = arrayOfNulls<String>(0)
  private val sink = TerminalRenderClusterSink { column, text -> clusters[column] = text }

  fun lineId(): Long? {
    if (nativeView == null) {
      val model = view.outputModels.regular
      return model.getLineByOffset(model.cursorOffset) - org.jetbrains.plugins.terminal.view.TerminalLineIndex.of(0)
    }
    val session = view.sessionDeferred.getNow() as? KetraTermTerminalSession ?: return null
    var id: Long? = null
    session.terminalSession.readRenderFrame { frame ->
      if (frame.activeBuffer == TerminalRenderBufferKind.PRIMARY) id = frame.lineId(frame.cursor.row)
    }
    return id
  }

  fun readLine(): Line? {
    if (nativeView == null) {
      val model = view.outputModels.regular
      val line = model.getLineByOffset(model.cursorOffset)
      val start = model.getStartOfLine(line)
      if (model.cursorOffset - start > MAX_LENGTH) return null
      return Line(line - org.jetbrains.plugins.terminal.view.TerminalLineIndex.of(0), model.getText(start, model.cursorOffset).toString())
    }
    val session = view.sessionDeferred.getNow() as? KetraTermTerminalSession ?: return null
    var result: Line? = null
    session.terminalSession.readRenderFrame { frame ->
      if (frame.activeBuffer != TerminalRenderBufferKind.PRIMARY || frame.columns > MAX_LENGTH) return@readRenderFrame
      var firstRow = frame.cursor.row
      while (firstRow > 0 && frame.lineWrapped(firstRow - 1)) firstRow--
      if ((frame.cursor.row - firstRow) * frame.columns + frame.cursor.column > MAX_LENGTH) return@readRenderFrame
      if (codes.size < frame.columns) {
        codes = IntArray(frame.columns)
        flags = IntArray(frame.columns)
        attrs = LongArray(frame.columns)
        clusters = arrayOfNulls(frame.columns)
      }
      val prefix = buildString {
        for (row in firstRow..frame.cursor.row) {
          clusters.fill(null)
          frame.copyLine(row, codes, attrWords = attrs, flags = flags, clusterSink = sink)
          val end = if (row == frame.cursor.row) frame.cursor.column else frame.columns
          for (column in 0 until end) {
            if (flags[column] and (TerminalRenderCellFlags.WIDE_TRAILING or TerminalRenderCellFlags.WRAP_PADDING) != 0) continue
            val cluster = clusters[column]
            if (cluster != null) append(cluster)
            else if (codes[column] != 0) appendCodePoint(codes[column])
            else append(' ')
          }
        }
      }
      if (prefix.length <= MAX_LENGTH) result = Line(frame.lineId(firstRow), prefix)
    }
    return result
  }

  companion object {
    private const val MAX_LENGTH = 1000
  }
}
