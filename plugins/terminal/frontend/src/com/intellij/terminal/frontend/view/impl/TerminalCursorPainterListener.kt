package com.intellij.terminal.frontend.view.impl

internal interface TerminalCursorPainterListener {
  /** Called on EDT */
  fun cursorPainted()
}