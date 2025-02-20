// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

@ApiStatus.Internal
interface TerminalEventsHandler {
  fun keyTyped(e: KeyEvent) {}
  fun keyPressed(e: KeyEvent) {}

  fun mousePressed(x: Int, y: Int, event: MouseEvent) {}
  fun mouseReleased(x: Int, y: Int, event: MouseEvent) {}
  fun mouseMoved(x: Int, y: Int, event: MouseEvent) {}
  fun mouseDragged(x: Int, y: Int, event: MouseEvent) {}
  fun mouseWheelMoved(x: Int, y: Int, event: MouseWheelEvent) {}
}
