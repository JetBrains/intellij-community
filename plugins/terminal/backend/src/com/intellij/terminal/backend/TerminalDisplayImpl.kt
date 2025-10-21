// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend

import com.intellij.util.EventDispatcher
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.TerminalSelection
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TerminalDisplayImpl(private val settings: DefaultSettingsProvider) : TerminalDisplay {
  private var cursorX: Int = 0
  private var cursorY: Int = 0

  var isCursorVisible: Boolean = true
    private set
  var cursorShape: CursorShape? = null
    private set
  var mouseMode: MouseMode = MouseMode.MOUSE_REPORTING_NONE
    private set
  var mouseFormat: MouseFormat = MouseFormat.MOUSE_FORMAT_XTERM
    private set
  var isBracketedPasteMode: Boolean = false
    private set
  var windowTitleText: String = ""
    private set

  private val dispatcher = EventDispatcher.create(TerminalDisplayListener::class.java)

  fun addListener(listener: TerminalDisplayListener) {
    dispatcher.addListener(listener)
  }

  override fun setCursor(x: Int, y: Int) {
    val zeroBasedY = y - 1
    if (x != cursorX || zeroBasedY != cursorY) {
      cursorX = x
      cursorY = zeroBasedY
      dispatcher.multicaster.cursorPositionChanged(x, zeroBasedY)
    }
  }

  override fun setCursorShape(cursorShape: CursorShape?) {
    if (this.cursorShape != cursorShape) {
      this.cursorShape = cursorShape
      dispatcher.multicaster.cursorShapeChanged(cursorShape)
    }
  }

  override fun setCursorVisible(isCursorVisible: Boolean) {
    if (this.isCursorVisible != isCursorVisible) {
      this.isCursorVisible = isCursorVisible
      dispatcher.multicaster.cursorVisibilityChanged(isCursorVisible)
    }
  }

  override fun getWindowTitle(): String? {
    return windowTitleText
  }

  override fun setWindowTitle(windowTitle: String) {
    if (this.windowTitleText != windowTitle) {
      this.windowTitleText = windowTitle
      dispatcher.multicaster.windowTitleChanged(windowTitle)
    }
  }

  override fun terminalMouseModeSet(mouseMode: MouseMode) {
    if (this.mouseMode != mouseMode) {
      this.mouseMode = mouseMode
      dispatcher.multicaster.mouseModeChanged(mouseMode)
    }
  }

  override fun setMouseFormat(mouseFormat: MouseFormat) {
    if (this.mouseFormat != mouseFormat) {
      this.mouseFormat = mouseFormat
      dispatcher.multicaster.mouseFormatChanged(mouseFormat)
    }
  }

  override fun setBracketedPasteMode(bracketedPasteModeEnabled: Boolean) {
    if (isBracketedPasteMode != bracketedPasteModeEnabled) {
      isBracketedPasteMode = bracketedPasteModeEnabled
      dispatcher.multicaster.bracketedPasteModeChanged(bracketedPasteModeEnabled)
    }
  }

  override fun beep() {
    dispatcher.multicaster.beep()
  }

  override fun useAlternateScreenBuffer(useAlternateScreenBuffer: Boolean) {

  }

  override fun scrollArea(scrollRegionTop: Int, scrollRegionSize: Int, dy: Int) {

  }

  override fun getSelection(): TerminalSelection? {
    return null
  }

  override fun ambiguousCharsAreDoubleWidth(): Boolean {
    return settings.ambiguousCharsAreDoubleWidth()
  }
}