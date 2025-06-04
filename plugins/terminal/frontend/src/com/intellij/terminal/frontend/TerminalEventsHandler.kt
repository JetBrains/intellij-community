package com.intellij.terminal.frontend

import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

internal interface TerminalEventsHandler {
  fun keyTyped(e: TimedKeyEvent) {}
  fun keyPressed(e: TimedKeyEvent) {}

  fun mousePressed(x: Int, y: Int, event: MouseEvent) {}
  fun mouseReleased(x: Int, y: Int, event: MouseEvent) {}
  fun mouseMoved(x: Int, y: Int, event: MouseEvent) {}
  fun mouseDragged(x: Int, y: Int, event: MouseEvent) {}
  fun mouseWheelMoved(x: Int, y: Int, event: MouseWheelEvent) {}
}