// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.JediTerminal
import java.awt.Toolkit

internal class ModelUpdatingTerminalDisplay(private val model: TerminalModel,
                                            private val settings: JBTerminalSystemSettingsProviderBase) : TerminalDisplay {

  override fun setCursor(x: Int, y: Int) {
    model.setCursor(x, y - 1)
  }

  override fun setCursorShape(cursorShape: CursorShape) {
    model.cursorShape = cursorShape
  }

  override fun beep() {
    if (model.isCommandRunning && settings.audibleBell()) {
      Toolkit.getDefaultToolkit().beep()
    }
  }

  override fun requestResize(newWinSize: TermSize,
                             origin: RequestOrigin,
                             cursorX: Int,
                             cursorY: Int,
                             resizeHandler: JediTerminal.ResizeHandler) {
    val oldWidth = model.width
    val oldHeight = model.height
    val delegatingResizeHandler = JediTerminal.ResizeHandler { newTermWidth, newTermHeight, newCursorX, newCursorY ->
      resizeHandler.sizeUpdated(newTermWidth, newTermHeight, newCursorX, newCursorY)
      if (oldWidth != newTermWidth || oldHeight != newTermHeight) {
        model.terminalListeners.forEach { it.onSizeChanged(newTermWidth, newTermHeight) }
      }
    }
    model.textBuffer.resize(newWinSize, origin, cursorX, cursorY, delegatingResizeHandler, null)
  }

  override fun scrollArea(scrollRegionTop: Int, scrollRegionSize: Int, dy: Int) {}

  override fun setCursorVisible(isCursorVisible: Boolean) {
    model.isCursorVisible = isCursorVisible
  }

  override fun useAlternateScreenBuffer(useAlternateScreenBuffer: Boolean) {
    model.useAlternateBuffer = useAlternateScreenBuffer
  }

  override fun setBlinkingCursor(isCursorBlinking: Boolean) {
    model.isCursorBlinking = isCursorBlinking
  }

  override fun getWindowTitle(): String = model.windowTitle

  override fun setWindowTitle(windowTitle: String) {
    model.windowTitle = windowTitle
  }

  override fun terminalMouseModeSet(mouseMode: MouseMode) {
    model.mouseMode = mouseMode
  }

  override fun setMouseFormat(mouseFormat: MouseFormat) {
    model.mouseFormat = mouseFormat
  }

  override fun setBracketedPasteMode(enabled: Boolean) {
    model.isBracketedPasteMode = enabled
  }

  override fun ambiguousCharsAreDoubleWidth(): Boolean = settings.ambiguousCharsAreDoubleWidth()
}