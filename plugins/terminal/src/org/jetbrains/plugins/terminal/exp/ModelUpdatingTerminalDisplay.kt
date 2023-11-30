// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.TerminalSelection
import java.awt.Toolkit

internal class ModelUpdatingTerminalDisplay(private val model: TerminalModel,
                                            private val settings: JBTerminalSystemSettingsProviderBase) : TerminalDisplay {

  override fun setCursor(x: Int, y: Int) {
    model.setCursor(x, y)
  }

  override fun setCursorShape(cursorShape: CursorShape) {
    model.cursorShape = cursorShape
  }

  override fun beep() {
    if (model.isCommandRunning && settings.audibleBell()) {
      Toolkit.getDefaultToolkit().beep()
    }
  }

  override fun onResize(newTermSize: TermSize, origin: RequestOrigin) {
    model.terminalListeners.forEach { it.onSizeChanged(newTermSize.columns, newTermSize.rows) }
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

  override fun getSelection(): TerminalSelection? = null

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