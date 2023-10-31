// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.StyledTextConsumer
import com.jediterm.terminal.Terminal
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalModelListener
import com.jediterm.terminal.model.TerminalTextBuffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min

class TerminalModel(private val textBuffer: TerminalTextBuffer, val styleState: StyleState) {
  val width: Int
    get() = textBuffer.width
  val height: Int
    get() = textBuffer.height

  @Volatile
  var cursorX: Int = 0
    private set
  @Volatile
  var cursorY: Int = 0
    private set

  var isCommandRunning: Boolean = false

  var cursorShape: CursorShape = CursorShape.BLINK_BLOCK
    set(value) {
      if (value != field) {
        field = value
        cursorListeners.forEach { it.onShapeChanged(value) }
      }
    }

  var isCursorVisible: Boolean = true
    set(value) {
      if (value != field) {
        field = value
        cursorListeners.forEach { it.onVisibilityChanged(value) }
      }
    }

  var isCursorBlinking: Boolean = true
    set(value) {
      if (value != field) {
        field = value
        cursorListeners.forEach { it.onBlinkingChanged(value) }
      }
    }

  var isBracketedPasteMode: Boolean = false
    set(value) {
      if (value != field) {
        field = value
        terminalListeners.forEach { it.onBracketedPasteModeChanged(value) }
      }
    }

  var mouseMode = MouseMode.MOUSE_REPORTING_NONE
    set(value) {
      if (value != field) {
        field = value
        cursorListeners.forEach { it.onMouseModeChanged(value) }
      }
    }

  var mouseFormat = MouseFormat.MOUSE_FORMAT_XTERM
    set(value) {
      if (value != field) {
        field = value
        cursorListeners.forEach { it.onMouseFormatChanged(value) }
      }
    }

  var windowTitle: String = "Terminal"
    set(value) {
      if (value != field) {
        field = value
        terminalListeners.forEach { it.onWindowTitleChanged(value) }
      }
    }

  var useAlternateBuffer: Boolean = textBuffer.isUsingAlternateBuffer
    set(value) {
      if (field != value) {
        field = value
        terminalListeners.forEach { it.onAlternateBufferChanged(value) }
      }
    }

  val historyLinesCount: Int
    get() = textBuffer.historyLinesCount

  val screenLinesCount: Int
    get() = textBuffer.screenLinesCount

  fun setCursor(x: Int, y: Int) {
    if (x != cursorX || y != cursorY) {
      cursorX = x
      cursorY = y
      cursorListeners.forEach { it.onPositionChanged(x, y) }
    }
  }

  fun charAt(x: Int, y: Int): Char = textBuffer.getCharAt(x, y)

  fun getLine(index: Int): TerminalLine = textBuffer.getLine(index)

  fun processHistoryAndScreenLines(scrollOrigin: Int, maxLinesToProcess: Int, consumer: StyledTextConsumer) {
    textBuffer.processHistoryAndScreenLines(scrollOrigin, maxLinesToProcess, consumer)
  }

  fun processScreenLines(yStart: Int, count: Int, consumer: StyledTextConsumer) {
    textBuffer.processScreenLines(yStart, count, consumer)
  }

  fun getAllText(updatedCursorX: Int = cursorX, updatedCursorY: Int = cursorY): String {
    return getLinesText(-historyLinesCount, screenLinesCount, updatedCursorX, updatedCursorY)
  }

  private fun getLinesText(fromLine: Int, toLine: Int, updatedCursorX: Int, updatedCursorY: Int): String {
    val builder = StringBuilder()
    for (ind in fromLine until toLine) {
      var text = getLine(ind).text
      if (ind == updatedCursorY - 1) {
        text = text.substring(0, min(updatedCursorX, text.length))
      }
      builder.append(text)
      if (text.isNotEmpty()) {
        builder.append('\n')
      }
    }
    return if (builder.isNotEmpty()) {
      builder.dropLast(1).toString()  // remove last line break
    }
    else builder.toString()
  }

  //-------------------MODIFICATION METHODS------------------------------------------------

  fun clearAllAndMoveCursorToTopLeftCorner(terminal: Terminal) {
    terminal.eraseInDisplay(2)
    terminal.cursorPosition(1, 1)
    textBuffer.clearHistory()
  }

  fun lockContent() = textBuffer.lock()

  fun unlockContent() = textBuffer.unlock()

  inline fun <T> withContentLock(callable: () -> T): T {
    lockContent()
    return try {
      callable()
    }
    finally {
      unlockContent()
    }
  }

  //---------------------LISTENERS----------------------------------------------

  internal val terminalListeners: MutableList<TerminalListener> = CopyOnWriteArrayList()
  private val cursorListeners: MutableList<CursorListener> = CopyOnWriteArrayList()

  fun addContentListener(listener: ContentListener, parentDisposable: Disposable? = null) {
    val terminalListener = TerminalModelListener { listener.onContentChanged() }
    textBuffer.addModelListener(terminalListener)
    if (parentDisposable != null) {
      Disposer.register(parentDisposable) {
        textBuffer.removeModelListener(terminalListener)
      }
    }
  }

  fun addTerminalListener(listener: TerminalListener, parentDisposable: Disposable? = null) {
    terminalListeners.add(listener)
    if (parentDisposable != null) {
      Disposer.register(parentDisposable) {
        terminalListeners.remove(listener)
      }
    }
  }

  fun addCursorListener(listener: CursorListener, parentDisposable: Disposable? = null) {
    cursorListeners.add(listener)
    if (parentDisposable != null) {
      Disposer.register(parentDisposable) {
        cursorListeners.remove(listener)
      }
    }
  }

  interface ContentListener {
    fun onContentChanged() {}
  }

  interface TerminalListener {
    fun onSizeChanged(width: Int, height: Int) {}

    fun onWindowTitleChanged(title: String) {}

    fun onAlternateBufferChanged(enabled: Boolean) {}

    fun onBracketedPasteModeChanged(bracketed: Boolean) {}
  }

  interface CursorListener {
    fun onPositionChanged(cursorX: Int, cursorY: Int) {}

    fun onShapeChanged(shape: CursorShape) {}

    fun onVisibilityChanged(visible: Boolean) {}

    fun onBlinkingChanged(blinking: Boolean) {}

    fun onMouseModeChanged(mode: MouseMode) {}

    fun onMouseFormatChanged(format: MouseFormat) {}
  }

  companion object {
    const val MIN_WIDTH = 5
    const val MIN_HEIGHT = 2
  }
}