// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import com.intellij.application.options.editor.EditorOptionsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.terminal.CursorShape
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.session.TerminalModel
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

internal class TerminalCaretModel(
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
    terminalModel.addCursorListener(object : TerminalModel.CursorListener {
      override fun onPositionChanged(cursorX: Int, cursorY: Int) {
        scheduleUpdate()
      }

      override fun onVisibilityChanged(visible: Boolean) {
        scheduleUpdate()
      }

      override fun onShapeChanged(shape: CursorShape?) {
        scheduleUpdate()
      }
    }, parentDisposable)
    val connection = ApplicationManager.getApplication().messageBus.connect(parentDisposable)
    connection.subscribe(EditorOptionsListener.APPEARANCE_CONFIGURABLE_TOPIC, EditorOptionsListener {
      scheduleUpdate() // update on enabling/disabling "Caret blinking" in "Editor | General | Appearance"
    })
  }

  fun addListener(listener: CaretListener, disposable: Disposable? = null) {
    listeners.add(listener)
    disposable?.let {
      Disposer.register(it) { listeners.remove(listener) }
    }
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
      val cursorShape = terminalModel.cursorShape ?: session.settings.cursorShape
      CaretState(position, cursorShape.isBlinking)
    }
  }

  fun getCaretPosition(): LogicalPosition? {
    return session.model.withContentLock {
      calculateCaretPosition(terminalModel.cursorX, terminalModel.cursorY)
    }
  }

  private fun calculateCaretPosition(cursorX: Int, cursorY: Int): LogicalPosition? {
    // There can be no active block at this moment, because it is not created yet, return null in this case.
    val activeBlock = outputModel.getActiveBlock() ?: return null
    // cursor position in the TextBuffer is relative to the output start
    val outputStartOffset = activeBlock.outputStartOffset
    // Right after the command start, there can be no line break after the command.
    // So, the output start offset is out of the document text bounds. Return null in this case.
    if (outputStartOffset > editor.document.textLength) {
      return null
    }
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
