package com.intellij.terminal.frontend.view.impl

import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyEvent

@ApiStatus.Internal
interface TerminalKeyEventsHandler {
  fun keyTyped(e: KeyEvent) {}
  fun keyPressed(e: KeyEvent) {}
}

internal fun TerminalKeyEventsHandler.handleKeyEvent(e: KeyEvent) {
  if (e.id == KeyEvent.KEY_TYPED) {
    keyTyped(e)
  }
  else if (e.id == KeyEvent.KEY_PRESSED) {
    keyPressed(e)
  }
}
