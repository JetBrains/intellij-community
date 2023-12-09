// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class TerminalCaretModel(
  private val session: BlockTerminalSession,
  private val outputModel: TerminalOutputModel,
  private val editor: EditorEx,
  parentDisposable: Disposable,
) : TerminalModel.CursorListener, TerminalModel.TerminalListener {
  private val terminalModel: TerminalModel
    get() = session.model

  private val listeners: MutableList<CaretListener> = CopyOnWriteArrayList()

  private val updateScheduled: AtomicBoolean = AtomicBoolean(false)
  private val updateAlarm: Alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable)

  val state: CaretState
    get() = calculateState()

  init {
    terminalModel.addTerminalListener(this, parentDisposable)
    terminalModel.addCursorListener(this, parentDisposable)
  }

  fun addListener(listener: CaretListener, disposable: Disposable? = null) {
    listeners.add(listener)
    disposable?.let {
      Disposer.register(it) { listeners.remove(listener) }
    }
  }

  override fun onPositionChanged(cursorX: Int, cursorY: Int) {
    scheduleUpdate()
  }

  override fun onVisibilityChanged(visible: Boolean) {
    scheduleUpdate()
  }

  override fun onBlinkingChanged(blinking: Boolean) {
    scheduleUpdate()
  }

  override fun onCommandRunningChanged(isRunning: Boolean) {
    scheduleUpdate()
  }

  private fun scheduleUpdate() {
    if (updateScheduled.compareAndSet(false, true)) {
      updateAlarm.addRequest(::doUpdate, 50)
    }
  }

  @RequiresEdt
  private fun doUpdate() {
    try {
      val state = calculateState()
      for (listener in listeners) {
        listener.caretStateChanged(state)
      }
    }
    finally {
      updateScheduled.set(false)
    }
  }

  private fun calculateState(): CaretState {
    return session.model.withContentLock {
      val position = if (terminalModel.isCursorVisible) {
        calculateCaretPosition(terminalModel.cursorX, terminalModel.cursorY)
      }
      else null
      CaretState(position, terminalModel.isCursorBlinking)
    }
  }

  private fun calculateCaretPosition(cursorX: Int, cursorY: Int): LogicalPosition {
    val outputStartOffset = outputModel.getLastBlock()!!.outputStartOffset
    // cursor position in the TextBuffer is relative to the output start
    val blockStartLine = editor.document.getLineNumber(outputStartOffset)
    val historyLines = if (terminalModel.useAlternateBuffer) 0 else terminalModel.historyLinesCount
    val blockLine = historyLines + cursorY - 1
    return LogicalPosition(blockStartLine + blockLine, cursorX)
  }

  /**
   * @param [position] null means, that caret is not showing
   */
  data class CaretState(val position: LogicalPosition?, val isBlinking: Boolean = true)

  interface CaretListener {
    @RequiresEdt
    fun caretStateChanged(state: CaretState) {}
  }
}