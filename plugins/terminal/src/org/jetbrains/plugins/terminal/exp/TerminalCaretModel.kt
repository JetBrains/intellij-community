// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CopyOnWriteArrayList

class TerminalCaretModel(private val session: TerminalSession,
                         private val outputModel: TerminalOutputModel,
                         private val editor: EditorEx) : ShellCommandListener, TerminalModel.CursorListener, Disposable {
  private val terminalModel: TerminalModel
    get() = session.model

  private val listeners: MutableList<CaretListener> = CopyOnWriteArrayList()

  /** Null means, that caret is not showing */
  @Volatile
  var caretPosition: LogicalPosition? = LogicalPosition(0, 0)
    private set
  @Volatile
  var isBlinking: Boolean = terminalModel.isCursorBlinking
    private set

  init {
    session.addCommandListener(this, parentDisposable = this)
    terminalModel.addCursorListener(this, parentDisposable = this)
  }

  fun addListener(listener: CaretListener, disposable: Disposable? = null) {
    listeners.add(listener)
    disposable?.let {
      Disposer.register(it) { listeners.remove(listener) }
    }
  }

  override fun onPositionChanged(cursorX: Int, cursorY: Int) {
    if (terminalModel.isCommandRunning && terminalModel.isCursorVisible) {
      updateCaretPosition(cursorX, cursorY)
    }
    else updateCaretPosition(null)
  }

  override fun onVisibilityChanged(visible: Boolean) {
    if (visible) {
      updateCaretPosition(terminalModel.cursorX, terminalModel.cursorY)
    }
    else updateCaretPosition(null)
  }

  override fun onBlinkingChanged(blinking: Boolean) {
    isBlinking = blinking
    listeners.forEach { it.caretBlinkingChanged(blinking) }
  }

  override fun commandStarted(command: String) {
    // place the caret to the next line after the command
    val position = calculateCaretPosition(0, 2)
    updateCaretPosition(position)
  }

  override fun commandFinished(command: String?, exitCode: Int, duration: Long?) {
    updateCaretPosition(newPosition = null)
  }

  override fun initialized() {
    updateCaretPosition(newPosition = null)
  }

  private fun calculateCaretPosition(cursorX: Int, cursorY: Int): LogicalPosition {
    val lastBlock = outputModel.getLastBlock() ?: error("No active block")
    // cursor position in the TextBuffer is relative to the command start
    val blockStartLine = editor.document.getLineNumber(lastBlock.commandStartOffset)
    val blockLine = terminalModel.historyLinesCount + cursorY - 1
    return LogicalPosition(blockStartLine + blockLine, cursorX)
  }

  private fun updateCaretPosition(cursorX: Int, cursorY: Int) {
    val position = calculateCaretPosition(cursorX, cursorY)
    updateCaretPosition(position)
  }

  private fun updateCaretPosition(newPosition: LogicalPosition?) {
    val oldPosition = caretPosition
    caretPosition = newPosition
    if (newPosition != oldPosition) {
      listeners.forEach {
        it.caretPositionChanged(oldPosition, newPosition)
      }
    }
  }

  override fun dispose() {}

  interface CaretListener {
    fun caretPositionChanged(oldPosition: LogicalPosition?, newPosition: LogicalPosition?) {}
    fun caretBlinkingChanged(isBlinking: Boolean) {}
  }
}