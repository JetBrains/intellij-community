package com.intellij.terminal.frontend

import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

@ApiStatus.Internal
interface TerminalEventsHandler {
  fun keyTyped(e: TimedKeyEvent) {}
  fun keyPressed(e: TimedKeyEvent) {}

  fun mousePressed(x: Int, y: Int, event: MouseEvent) {}
  fun mouseReleased(x: Int, y: Int, event: MouseEvent) {}
  fun mouseMoved(x: Int, y: Int, event: MouseEvent) {}
  fun mouseDragged(x: Int, y: Int, event: MouseEvent) {}
  fun mouseWheelMoved(x: Int, y: Int, event: MouseWheelEvent) {}
}

internal fun TerminalEventsHandler.handleKeyEvent(e: TimedKeyEvent) {
  if (e.original.id == KeyEvent.KEY_TYPED) {
    keyTyped(e)
  }
  else if (e.original.id == KeyEvent.KEY_PRESSED) {
    keyPressed(e)
  }
}
