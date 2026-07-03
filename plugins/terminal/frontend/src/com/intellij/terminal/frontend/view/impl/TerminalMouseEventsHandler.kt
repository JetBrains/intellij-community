package com.intellij.terminal.frontend.view.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

@ApiStatus.Internal
@VisibleForTesting
interface TerminalMouseEventsHandler {
  fun mousePressed(x: Int, y: Int, event: MouseEvent) {}
  fun mouseReleased(x: Int, y: Int, event: MouseEvent) {}
  fun mouseMoved(x: Int, y: Int, event: MouseEvent) {}
  fun mouseDragged(x: Int, y: Int, event: MouseEvent) {}
  fun mouseWheelMoved(x: Int, y: Int, event: MouseWheelEvent) {}
}
