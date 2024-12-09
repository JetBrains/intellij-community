// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session.output

import com.intellij.util.EventDispatcher
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.TerminalSelection

internal class TerminalDisplayImpl : TerminalDisplay {
  private var cursorX: Int = 0
  private var cursorY: Int = 0

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

  }

  override fun beep() {

  }

  override fun scrollArea(scrollRegionTop: Int, scrollRegionSize: Int, dy: Int) {

  }

  override fun setCursorVisible(isCursorVisible: Boolean) {

  }

  override fun useAlternateScreenBuffer(useAlternateScreenBuffer: Boolean) {

  }

  override fun getWindowTitle(): String? {
    return "Local"
  }

  override fun setWindowTitle(windowTitle: String) {

  }

  override fun getSelection(): TerminalSelection? {
    return null
  }

  override fun terminalMouseModeSet(mouseMode: MouseMode) {

  }

  override fun setMouseFormat(mouseFormat: MouseFormat) {

  }

  override fun ambiguousCharsAreDoubleWidth(): Boolean {
    return true
  }
}