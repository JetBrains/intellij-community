package com.intellij.terminal.frontend

internal interface TerminalCursorPainterListener {
  /** Called on EDT */
  fun cursorPainted()
}