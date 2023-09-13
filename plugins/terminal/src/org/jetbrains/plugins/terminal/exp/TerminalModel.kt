// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.StyledTextConsumer
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.*
import com.jediterm.terminal.model.JediTerminal.ResizeHandler
import java.awt.Dimension
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

  var isScrollingEnabled: Boolean = true
    set(value) {
      if (value != field) {
        field = value
        terminalListeners.forEach { it.onScrollingChanged(value) }
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

  var useAlternateBuffer: Boolean
    get() = textBuffer.isUsingAlternateBuffer
    set(value) {
      if (textBuffer.isUsingAlternateBuffer != value) {
        textBuffer.useAlternateBuffer(value)
        terminalListeners.forEach { it.onAlternateBufferChanged(value) }
      }
    }

  @Volatile
  var promptText: String = ""
    set(value) {
      if (value != field) {
        field = value
        terminalListeners.forEach { it.onPromptTextChanged(value) }
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

  fun getScreenText(): String {
    return getLinesText(0, screenLinesCount, cursorX, cursorY)
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

  fun scrollArea(scrollRegionTop: Int, scrollRegionBottom: Int, dy: Int) {
    textBuffer.scrollArea(scrollRegionTop, dy, scrollRegionBottom)
  }

  fun writeString(x: Int, y: Int, buffer: CharArray) {
    textBuffer.writeString(x, y, CharBuffer(buffer, 0, buffer.size))
  }

  fun insertLines(y: Int, count: Int, scrollRegionBottom: Int) {
    textBuffer.insertLines(y, count, scrollRegionBottom)
  }

  fun insertBlankCharacters(x: Int, y: Int, count: Int) {
    textBuffer.insertBlankCharacters(x, y, count)
  }

  fun clearAll() = textBuffer.clearAll()

  fun clearLines(beginY: Int, endY: Int) {
    textBuffer.clearLines(beginY, endY)
  }

  fun clearHistory() = textBuffer.clearHistory()

  fun eraseLine(line: Int, limit: Int) {
    textBuffer.getLine(line).deleteCharacters(limit)
  }

  fun eraseCharacters(leftX: Int, rightX: Int, y: Int) {
    textBuffer.eraseCharacters(leftX, rightX, y)
  }

  fun deleteCharacters(x: Int, y: Int, count: Int) {
    textBuffer.deleteCharacters(x, y, count)
  }

  fun deleteLines(y: Int, count: Int, scrollRegionBottom: Int) {
    textBuffer.deleteLines(y, count, scrollRegionBottom)
  }

  fun setLineWrapped(line: Int, wrapped: Boolean) {
    textBuffer.getLine(line).isWrapped = wrapped
  }

  fun resize(newSize: Dimension,
             origin: RequestOrigin,
             cursorX: Int,
             cursorY: Int,
             selection: TerminalSelection?,
             resizeHandler: ResizeHandler) {
    val oldWidth = width
    val oldHeight = height
    val handler = ResizeHandler { newWidth, newHeight, newCursorX, newCursorY ->
      if (newWidth != oldWidth || newHeight != oldHeight) {
        terminalListeners.forEach { it.onSizeChanged(newWidth, newHeight) }
      }
      resizeHandler.sizeUpdated(newWidth, newHeight, newCursorX, newCursorY)
    }
    val termSize = TermSize(newSize.width, newSize.height)
    textBuffer.resize(termSize, origin, cursorX, cursorY, handler, selection)
  }

  fun moveScreenLinesToHistory() {
    // TODO: make this method public
    val method = TerminalTextBuffer::class.java.getDeclaredMethod("moveScreenLinesToHistory")
                 ?: error("Not found method: moveScreenLinesToHistory")
    method.isAccessible = true
    method.invoke(textBuffer)
  }

  fun clearAllExceptPrompt(promptLines: Int = 1) {
    textBuffer.scrollArea(1, promptLines - cursorY, height)
    textBuffer.clearHistory()
    cursorY = promptLines
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

  private val terminalListeners: MutableList<TerminalListener> = CopyOnWriteArrayList()
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

    fun onScrollingChanged(enabled: Boolean) {}

    fun onAlternateBufferChanged(enabled: Boolean) {}

    fun onBracketedPasteModeChanged(bracketed: Boolean) {}

    fun onPromptTextChanged(newText: String) {}
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