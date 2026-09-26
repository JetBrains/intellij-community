package com.intellij.terminal.frontend.view.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.awt.event.MouseEvent

@ApiStatus.Internal
@VisibleForTesting
interface TerminalMouseEventsHandler {
  fun onMouseEvent(x: Int, y: Int, event: MouseEvent) {}
}
